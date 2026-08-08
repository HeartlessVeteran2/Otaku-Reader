package app.otakureader.data.tracking.repository

import app.otakureader.core.database.dao.TrackerSyncDao
import app.otakureader.core.database.entity.SyncConfigurationEntity
import app.otakureader.core.database.entity.TrackerSyncStateEntity
import app.otakureader.data.tracking.TrackManager
import app.otakureader.domain.model.ConflictResolution
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.SyncConfiguration
import app.otakureader.domain.model.SyncDirection
import app.otakureader.domain.model.SyncStatus
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackerSyncState
import app.otakureader.domain.repository.TrackerSyncRepository
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.domain.tracking.Tracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Implements bidirectional sync between the local database and external trackers.
 *
 * Sync logic:
 * - PENDING states are entries that have local changes not yet pushed to the tracker.
 * - On sync, both local and remote states are compared against [TrackerSyncState.lastSuccessfulSync].
 * - If both sides changed → conflict, resolved via [ConflictResolution] strategy.
 * - If only local changed → push to remote.
 * - If only remote changed → pull to local.
 * - If neither changed → mark SYNCED without network round-trip.
 */
@Singleton
class TrackerSyncRepositoryImpl @Inject constructor(
    private val trackerSyncDao: TrackerSyncDao,
    private val trackRepository: TrackRepository,
    private val trackManager: TrackManager
) : TrackerSyncRepository {

    // ── Configuration ──────────────────────────────────────────────────────

    override fun getSyncConfigurations(): Flow<List<SyncConfiguration>> =
        trackerSyncDao.getSyncConfigurations().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun updateSyncConfiguration(config: SyncConfiguration) {
        val existing = trackerSyncDao.getSyncConfiguration(config.trackerId)
        if (existing != null) {
            trackerSyncDao.updateSyncConfiguration(
                existing.copy(
                    enabled = config.enabled,
                    syncDirection = config.syncDirection.ordinal,
                    conflictResolution = config.conflictResolution.ordinal,
                    autoSyncInterval = config.autoSyncInterval,
                    syncOnChapterRead = config.syncOnChapterRead,
                    syncOnMarkComplete = config.syncOnMarkComplete
                )
            )
        } else {
            trackerSyncDao.insertSyncConfiguration(config.toEntity())
        }
    }

    override suspend fun enableTrackerSync(trackerId: Int, enabled: Boolean) {
        val existing = trackerSyncDao.getSyncConfiguration(trackerId)
        if (existing != null) {
            trackerSyncDao.setSyncEnabled(trackerId, enabled)
        } else {
            trackerSyncDao.insertSyncConfiguration(
                SyncConfigurationEntity(
                    trackerId = trackerId,
                    enabled = enabled,
                    syncDirection = SyncDirection.TWO_WAY.ordinal,
                    conflictResolution = ConflictResolution.ASK.ordinal
                )
            )
        }
    }

    // ── Sync State ─────────────────────────────────────────────────────────

    override fun getSyncStateForManga(mangaId: Long): Flow<List<TrackerSyncState>> =
        trackerSyncDao.getSyncStateForManga(mangaId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getPendingSyncs(): Flow<List<TrackerSyncState>> =
        trackerSyncDao.getPendingSyncs().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Serializes every read-decide-write on one sync-state row.
     *
     * Re-reading the row after the network call narrows the window in which a chapter read can be
     * lost, but it does not close it: the re-read and the write that follows are two separate
     * suspending calls, and a `recordLocalChange` landing between them is still overwritten. The
     * lock is what makes that pair atomic — which is the rule this codebase already writes down,
     * that a liveness check and the write it guards belong inside the same lock rather than each
     * taking one separately.
     *
     * **Never held across a network call.** [pushLocalToTracker] does `tracker.update(...)`
     * outside the lock and takes it only for the write, so a slow tracker cannot block a chapter
     * from being recorded.
     *
     * This serializes writers inside this process, which is every production writer of the table
     * except [app.otakureader.data.backup.BackupRestorer], and a restore is not concurrent with
     * syncing. It is deliberately not a database-level compare-and-swap; if a second writing
     * process ever appears, this becomes insufficient and the guard has to move into SQL.
     *
     * Striped rather than a map of per-row mutexes for the reason the metadata cache uses the same
     * shape: a fixed array is bounded by construction, and two different rows colliding on a
     * stripe costs a little parallelism and can never cause a wrong result.
     */
    private fun rowLock(mangaId: Long, trackerId: Int): Mutex =
        // floorMod, not %: the product can overflow to a negative and index out of bounds.
        rowLocks[Math.floorMod(mangaId * PRIME_MULTIPLIER + trackerId, STRIPE_COUNT)]

    private val rowLocks = Array(STRIPE_COUNT) { Mutex() }

    /**
     * Records that the user read up to [chapterRead] locally, for later push.
     *
     * ### It has to write the [TrackEntry], not only the sync-state row
     *
     * This used to update `localLastChapterRead` and nothing else — and the push does not read
     * that field. [syncManga]'s local-wins branch builds its payload from
     * `trackRepository.getEntry(...)`, so progress recorded here reached a column that only
     * conflict detection ever looks at. Finishing a chapter marked the sync PENDING, pushed the
     * *previous* entry unchanged, and then marked itself SYNCED — so reading never advanced
     * progress on any tracker, and the per-tracker chips on the details screen never moved either,
     * since those render the same [TrackEntry].
     *
     * ### Progress only ever goes forward
     *
     * Re-reading an old chapter is normal, and it must not tell AniList you have un-read fifty
     * chapters. Both writes take the maximum rather than the incoming value.
     *
     * The sync-state row needs the same guard for a second reason: `localLastChapterRead` feeds
     * the conflict check, so a re-read that wrote a lower number could manufacture a conflict
     * against a remote that had not changed at all, and hand the user a resolution prompt for a
     * disagreement they created by opening chapter 5.
     *
     * Status is recorded either way — dropping a manga is a real change even when no chapter was
     * read — which is why the timestamp and PENDING marking are outside the progress guard.
     */
    override suspend fun recordLocalChange(
        mangaId: Long,
        trackerId: Int,
        chapterRead: Float,
        status: MangaStatus
    ) {
        val now = Instant.now()

        rowLock(mangaId, trackerId).withLock {
            val entry = trackRepository.getEntry(mangaId, trackerId)
            if (entry != null && chapterRead > entry.lastChapterRead) {
                trackRepository.upsertEntry(entry.copy(lastChapterRead = chapterRead))
            }

            val existing = trackerSyncDao.getSyncState(mangaId, trackerId)
            if (existing != null) {
                trackerSyncDao.updateSyncState(
                    existing.copy(
                        localLastChapterRead = maxOf(existing.localLastChapterRead, chapterRead),
                        localStatus = status.ordinal,
                        localLastModified = now,
                        syncStatus = SyncStatus.PENDING.ordinal
                    )
                )
            }
            // If no sync state exists, local changes will be captured on first sync
        }
    }

    /**
     * The sync-state row as it stands *now*, but only if a local change landed since [snapshot].
     *
     * `syncManga` reads the row, makes a network call, then writes `snapshot.copy(...)`. That copy
     * carries whatever the row held before the request — so a chapter finished during the request
     * is silently overwritten, marked SYNCED, and never pushed. Re-reading afterwards is what makes
     * the write conditional on nothing having moved underneath it.
     *
     * Compared on `localLastModified` rather than the chapter number, because a status-only change
     * matters here too and would not move the number.
     */
    private suspend fun localChangeSince(snapshot: TrackerSyncStateEntity): TrackerSyncStateEntity? =
        trackerSyncDao.getSyncState(snapshot.mangaId, snapshot.trackerId)
            ?.takeIf { it.localLastModified > snapshot.localLastModified }

    /**
     * Local wins: send the entry to the tracker and record what came back.
     *
     * @return a non-null result when the caller should return early, or null to continue.
     */
    private suspend fun pushLocalToTracker(
        mangaId: Long,
        trackerId: Int,
        tracker: Tracker,
        syncState: TrackerSyncStateEntity,
        now: Instant,
    ): TrackerSyncRepository.SyncResult? {
        val localEntry = trackRepository.getEntry(mangaId, trackerId)
            // Matches the auto-create path in syncManga, which returns this same failure for the
            // same condition. Returning "Sync successful" after pushing nothing told the caller
            // progress had been sent when no entry existed to send.
            ?: return TrackerSyncRepository.SyncResult(false, "No local entry found for manga")

        // Outside the lock: a slow tracker must not block a chapter from being recorded.
        val updated = tracker.update(localEntry)

        rowLock(mangaId, trackerId).withLock {
            val concurrent = localChangeSince(syncState)
            trackerSyncDao.updateSyncState(
                (concurrent ?: syncState).copy(
                    remoteLastChapterRead = updated.lastChapterRead,
                    remoteTotalChapters = updated.totalChapters,
                    remoteStatus = MangaStatus.UNKNOWN.ordinal,
                    remoteLastModified = now,
                    // A chapter finished while the push was in flight has not been sent, so the
                    // row stays PENDING and `lastSuccessfulSync` is deliberately left alone.
                    // Advancing it would put it *after* the new `localLastModified`, and
                    // `localChanged` is exactly that comparison — the next sync would decide there
                    // was nothing to send, and the read would be lost with no error anywhere.
                    syncStatus = if (concurrent != null) {
                        SyncStatus.PENDING.ordinal
                    } else {
                        SyncStatus.SYNCED.ordinal
                    },
                    lastSuccessfulSync = if (concurrent != null) syncState.lastSuccessfulSync else now,
                    syncError = null
                )
            )
        }
        return null
    }

    /**
     * Remote wins: adopt the tracker's values locally.
     *
     * @return a non-null result when the caller should return early, or null to continue.
     */
    private suspend fun applyRemoteToLocal(
        mangaId: Long,
        trackerId: Int,
        remoteEntry: TrackEntry,
        syncState: TrackerSyncStateEntity,
        now: Instant,
    ): TrackerSyncRepository.SyncResult? {
        rowLock(mangaId, trackerId).withLock {
            val concurrent = localChangeSince(syncState)
            if (concurrent != null) {
                // A chapter finished while the remote was being fetched, so this is no longer a
                // clean remote-wins case: both sides have changed. Leave the row PENDING and
                // return, so the next sync routes it through conflict detection — overwriting
                // local here would discard the chapter the user just read in favour of a remote
                // value that predates it.
                //
                // The remote snapshot is deliberately NOT advanced. `remoteChanged` is
                // `remoteEntry.lastChapterRead != syncState.remoteLastChapterRead`, so writing the
                // freshly-fetched value here would make the next sync compare it against itself,
                // find them equal, and conclude the remote had not moved. With only `localChanged`
                // left true that sync would take the clean local-push path and overwrite the very
                // remote change this branch exists to preserve — no conflict prompt, no error.
                // Leaving the pre-fetch snapshot in place is what keeps that signal alive.
                //
                // `concurrent` already carries PENDING and a fresh `localLastModified` from
                // recordLocalChange, so there is nothing left to write.
                return TrackerSyncRepository.SyncResult(true, "Local change pending; sync deferred")
            }

            val localEntry = trackRepository.getEntry(mangaId, trackerId)
            val entryToUpsert = (localEntry ?: remoteEntry).copy(
                lastChapterRead = remoteEntry.lastChapterRead,
                totalChapters = remoteEntry.totalChapters,
                status = remoteEntry.status
            )
            trackRepository.upsertEntry(entryToUpsert)
            trackerSyncDao.updateSyncState(
                syncState.copy(
                    localLastChapterRead = remoteEntry.lastChapterRead,
                    localTotalChapters = remoteEntry.totalChapters,
                    localStatus = MangaStatus.UNKNOWN.ordinal,
                    localLastModified = now,
                    remoteLastChapterRead = remoteEntry.lastChapterRead,
                    remoteTotalChapters = remoteEntry.totalChapters,
                    remoteStatus = MangaStatus.UNKNOWN.ordinal,
                    remoteLastModified = now,
                    syncStatus = SyncStatus.SYNCED.ordinal,
                    lastSuccessfulSync = now,
                    syncError = null
                )
            )
        }
        return null
    }

    // ── Sync Operations ────────────────────────────────────────────────────

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override suspend fun syncManga(
        mangaId: Long,
        trackerId: Int
    ): TrackerSyncRepository.SyncResult {
        val tracker = trackManager.get(trackerId)
            ?: return TrackerSyncRepository.SyncResult(false, "Tracker not found")

        if (!tracker.isLoggedIn) {
            return TrackerSyncRepository.SyncResult(false, "Not logged in to tracker")
        }

        // Auto-create sync state from existing local entry if not yet initialized
        var syncState = trackerSyncDao.getSyncState(mangaId, trackerId)
        if (syncState == null) {
            val localEntries = trackRepository.observeEntriesForManga(mangaId).first()
            val localEntry = localEntries.firstOrNull { it.trackerId == trackerId }
                ?: return TrackerSyncRepository.SyncResult(false, "No local entry found for manga")

            val now = Instant.now()
            trackerSyncDao.insertSyncState(
                TrackerSyncStateEntity(
                    mangaId = mangaId,
                    trackerId = trackerId,
                    remoteId = localEntry.remoteId.toString(),
                    localLastChapterRead = localEntry.lastChapterRead,
                    localTotalChapters = localEntry.totalChapters,
                    localStatus = MangaStatus.UNKNOWN.ordinal,
                    localLastModified = now,
                    remoteLastChapterRead = localEntry.lastChapterRead,
                    remoteTotalChapters = localEntry.totalChapters,
                    remoteStatus = MangaStatus.UNKNOWN.ordinal,
                    remoteLastModified = null,
                    syncStatus = SyncStatus.PENDING.ordinal,
                    lastSyncAttempt = null,
                    lastSuccessfulSync = null,
                    syncError = null
                )
            )
            syncState = trackerSyncDao.getSyncState(mangaId, trackerId)
                ?: return TrackerSyncRepository.SyncResult(false, "Failed to initialize sync state")
        }

        val now = Instant.now()
        trackerSyncDao.updateSyncAttempt(syncState.id, SyncStatus.SYNCING.ordinal, now)

        return try {
            val remoteId = syncState.remoteId.toLongOrNull()
                ?: return TrackerSyncRepository.SyncResult(false, "Invalid remote ID")

            val remoteEntry = tracker.find(remoteId)
                ?: return TrackerSyncRepository.SyncResult(false, "Entry not found on tracker")

            val config = trackerSyncDao.getSyncConfiguration(trackerId)
            val conflictResolution = config?.let {
                ConflictResolution.entries.getOrElse(it.conflictResolution) { ConflictResolution.ASK }
            } ?: ConflictResolution.ASK

            val direction = config?.let {
                SyncDirection.entries.getOrElse(it.syncDirection) { SyncDirection.TWO_WAY }
            } ?: SyncDirection.TWO_WAY

            val lastSync = syncState.lastSuccessfulSync
            val localChanged = lastSync == null || syncState.localLastModified > lastSync
            // Detect remote change by diffing the freshly-fetched entry against our stored
            // remote snapshot. Using the stored remoteLastModified timestamp was wrong —
            // it's a locally-controlled value we set ourselves on every sync, so it can never
            // lag behind lastSuccessfulSync and would never detect real tracker-side changes.
            val remoteChanged = lastSync == null ||
                remoteEntry.lastChapterRead != syncState.remoteLastChapterRead ||
                remoteEntry.totalChapters != syncState.remoteTotalChapters

            // Conflict: both sides changed and their chapter progress diverged
            val hasConflict = localChanged && remoteChanged &&
                syncState.localLastChapterRead != remoteEntry.lastChapterRead

            if (hasConflict && conflictResolution == ConflictResolution.ASK) {
                trackerSyncDao.markSyncConflict(syncState.id, "Local and remote both changed")
                return TrackerSyncRepository.SyncResult(
                    success = false,
                    message = "Conflict detected: local ch ${syncState.localLastChapterRead}" +
                        " vs remote ch ${remoteEntry.lastChapterRead}",
                    hasConflict = true
                )
            }

            val useLocal: Boolean = when {
                hasConflict -> when (conflictResolution) {
                    ConflictResolution.LOCAL_WINS -> true
                    ConflictResolution.REMOTE_WINS -> false
                    ConflictResolution.NEWEST_WINS -> {
                        val remoteTime = syncState.remoteLastModified
                        remoteTime == null || syncState.localLastModified >= remoteTime
                    }
                    ConflictResolution.ASK -> error(
                        "ASK conflict resolution should have been handled before reaching this branch"
                    )
                }
                localChanged && direction != SyncDirection.REMOTE_TO_LOCAL -> true
                remoteChanged && direction != SyncDirection.LOCAL_TO_REMOTE -> false
                else -> {
                    // Nothing to sync; mark as SYNCED
                    trackerSyncDao.markSyncSuccess(syncState.id, SyncStatus.SYNCED.ordinal, now)
                    return TrackerSyncRepository.SyncResult(true, "Already in sync")
                }
            }

            if (useLocal) {
                pushLocalToTracker(mangaId, trackerId, tracker, syncState, now)
                    ?.let { return it }
            } else {
                applyRemoteToLocal(mangaId, trackerId, remoteEntry, syncState, now)
                    ?.let { return it }
            }

            TrackerSyncRepository.SyncResult(true, "Sync successful")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trackerSyncDao.updateSyncAttempt(syncState.id, SyncStatus.ERROR.ordinal, now)
            TrackerSyncRepository.SyncResult(false, e.message ?: "Sync failed")
        }
    }

    override suspend fun syncAllPending(): TrackerSyncRepository.SyncSummary {
        val pending = trackerSyncDao.getPendingSyncs().first()
        var attempted = 0
        var successful = 0
        var failed = 0
        var conflicts = 0

        pending.forEachIndexed { index, state ->
            // Stagger requests to avoid hitting tracker API rate limits / bans.
            if (index > 0) delay(BATCH_SYNC_STAGGER_MS)
            attempted++
            val result = syncManga(state.mangaId, state.trackerId)
            when {
                result.hasConflict -> conflicts++
                result.success -> successful++
                else -> failed++
            }
        }

        return TrackerSyncRepository.SyncSummary(attempted, successful, failed, conflicts)
    }

    @Suppress("LongMethod")
    override suspend fun resolveConflict(
        mangaId: Long,
        trackerId: Int,
        useLocal: Boolean
    ) {
        val syncState = trackerSyncDao.getSyncState(mangaId, trackerId) ?: return
        val tracker = trackManager.get(trackerId) ?: return
        val remoteId = syncState.remoteId.toLongOrNull() ?: return
        val now = Instant.now()

        if (useLocal) {
            val localEntry = trackRepository.getEntry(mangaId, trackerId) ?: return
            try {
                val updated = tracker.update(localEntry)
                trackerSyncDao.updateSyncState(
                    syncState.copy(
                        remoteLastChapterRead = updated.lastChapterRead,
                        remoteTotalChapters = updated.totalChapters,
                        remoteStatus = MangaStatus.UNKNOWN.ordinal,
                        remoteLastModified = now,
                        syncStatus = SyncStatus.SYNCED.ordinal,
                        lastSuccessfulSync = now,
                        syncError = null
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trackerSyncDao.updateSyncState(
                    syncState.copy(
                        syncStatus = SyncStatus.ERROR.ordinal,
                        lastSyncAttempt = now,
                        syncError = e.message
                    )
                )
            }
        } else {
            try {
                val remoteEntry = tracker.find(remoteId) ?: return
                val localEntry = trackRepository.getEntry(mangaId, trackerId)
                val entryToUpsert = (localEntry ?: remoteEntry).copy(
                    lastChapterRead = remoteEntry.lastChapterRead,
                    totalChapters = remoteEntry.totalChapters,
                    status = remoteEntry.status
                )
                trackRepository.upsertEntry(entryToUpsert)
                trackerSyncDao.updateSyncState(
                    syncState.copy(
                        localLastChapterRead = remoteEntry.lastChapterRead,
                        localTotalChapters = remoteEntry.totalChapters,
                        localStatus = MangaStatus.UNKNOWN.ordinal,
                        localLastModified = now,
                        remoteLastChapterRead = remoteEntry.lastChapterRead,
                        remoteTotalChapters = remoteEntry.totalChapters,
                        remoteStatus = MangaStatus.UNKNOWN.ordinal,
                        remoteLastModified = now,
                        syncStatus = SyncStatus.SYNCED.ordinal,
                        lastSuccessfulSync = now,
                        syncError = null
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trackerSyncDao.updateSyncState(
                    syncState.copy(
                        syncStatus = SyncStatus.ERROR.ordinal,
                        lastSyncAttempt = now,
                        syncError = e.message
                    )
                )
            }
        }
    }

    // ── Manual operations ──────────────────────────────────────────────────

    override suspend fun pushToTracker(
        mangaId: Long,
        trackerId: Int
    ): TrackerSyncRepository.SyncResult {
        val tracker = trackManager.get(trackerId)
            ?: return TrackerSyncRepository.SyncResult(false, "Tracker not found")

        if (!tracker.isLoggedIn) {
            return TrackerSyncRepository.SyncResult(false, "Not logged in to tracker")
        }

        val syncState = trackerSyncDao.getSyncState(mangaId, trackerId)
            ?: return TrackerSyncRepository.SyncResult(false, "No sync state found for manga")

        val remoteId = syncState.remoteId.toLongOrNull()
            ?: return TrackerSyncRepository.SyncResult(false, "Invalid remote ID")

        val localEntry = trackRepository.getEntry(mangaId, trackerId)
            ?: return TrackerSyncRepository.SyncResult(false, "No local entry found")

        val now = Instant.now()
        trackerSyncDao.updateSyncAttempt(syncState.id, SyncStatus.SYNCING.ordinal, now)

        return try {
            val updated = tracker.update(localEntry)
            trackerSyncDao.updateSyncState(
                syncState.copy(
                    remoteLastChapterRead = updated.lastChapterRead,
                    remoteTotalChapters = updated.totalChapters,
                    remoteStatus = MangaStatus.UNKNOWN.ordinal,
                    remoteLastModified = now,
                    syncStatus = SyncStatus.SYNCED.ordinal,
                    lastSuccessfulSync = now,
                    syncError = null
                )
            )
            TrackerSyncRepository.SyncResult(true, "Pushed to tracker successfully")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trackerSyncDao.updateSyncAttempt(syncState.id, SyncStatus.ERROR.ordinal, now)
            TrackerSyncRepository.SyncResult(false, e.message ?: "Push failed")
        }
    }

    override suspend fun pullFromTracker(
        mangaId: Long,
        trackerId: Int
    ): TrackerSyncRepository.SyncResult {
        val tracker = trackManager.get(trackerId)
            ?: return TrackerSyncRepository.SyncResult(false, "Tracker not found")

        if (!tracker.isLoggedIn) {
            return TrackerSyncRepository.SyncResult(false, "Not logged in to tracker")
        }

        val syncState = trackerSyncDao.getSyncState(mangaId, trackerId)
            ?: return TrackerSyncRepository.SyncResult(false, "No sync state found for manga")

        val remoteId = syncState.remoteId.toLongOrNull()
            ?: return TrackerSyncRepository.SyncResult(false, "Invalid remote ID")

        val now = Instant.now()
        trackerSyncDao.updateSyncAttempt(syncState.id, SyncStatus.SYNCING.ordinal, now)

        return try {
            val remoteEntry = tracker.find(remoteId)
                ?: return TrackerSyncRepository.SyncResult(false, "Entry not found on tracker")

            val localEntry = trackRepository.getEntry(mangaId, trackerId)
            val entryToUpsert = (localEntry ?: remoteEntry).copy(
                lastChapterRead = remoteEntry.lastChapterRead,
                totalChapters = remoteEntry.totalChapters,
                status = remoteEntry.status
            )
            trackRepository.upsertEntry(entryToUpsert)

            trackerSyncDao.updateSyncState(
                syncState.copy(
                    localLastChapterRead = remoteEntry.lastChapterRead,
                    localTotalChapters = remoteEntry.totalChapters,
                    localStatus = MangaStatus.UNKNOWN.ordinal,
                    localLastModified = now,
                    remoteLastChapterRead = remoteEntry.lastChapterRead,
                    remoteTotalChapters = remoteEntry.totalChapters,
                    remoteStatus = MangaStatus.UNKNOWN.ordinal,
                    remoteLastModified = now,
                    syncStatus = SyncStatus.SYNCED.ordinal,
                    lastSuccessfulSync = now,
                    syncError = null
                )
            )
            TrackerSyncRepository.SyncResult(true, "Pulled from tracker successfully")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trackerSyncDao.updateSyncAttempt(syncState.id, SyncStatus.ERROR.ordinal, now)
            TrackerSyncRepository.SyncResult(false, e.message ?: "Pull failed")
        }
    }

    // ── Mapping helpers ────────────────────────────────────────────────────

    private fun TrackerSyncStateEntity.toDomain() = TrackerSyncState(
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = remoteId,
        localLastChapterRead = localLastChapterRead,
        localTotalChapters = localTotalChapters,
        localStatus = MangaStatus.fromOrdinal(localStatus),
        localLastModified = localLastModified,
        remoteLastChapterRead = remoteLastChapterRead,
        remoteTotalChapters = remoteTotalChapters,
        remoteStatus = MangaStatus.fromOrdinal(remoteStatus),
        remoteLastModified = remoteLastModified,
        syncStatus = SyncStatus.entries.getOrElse(syncStatus) { SyncStatus.PENDING },
        lastSyncAttempt = lastSyncAttempt,
        lastSuccessfulSync = lastSuccessfulSync,
        syncError = syncError
    )

    private fun SyncConfigurationEntity.toDomain() = SyncConfiguration(
        trackerId = trackerId,
        enabled = enabled,
        syncDirection = SyncDirection.entries.getOrElse(syncDirection) { SyncDirection.TWO_WAY },
        conflictResolution = ConflictResolution.entries.getOrElse(conflictResolution) { ConflictResolution.ASK },
        autoSyncInterval = autoSyncInterval,
        syncOnChapterRead = syncOnChapterRead,
        syncOnMarkComplete = syncOnMarkComplete
    )

    private fun SyncConfiguration.toEntity() = SyncConfigurationEntity(
        trackerId = trackerId,
        enabled = enabled,
        syncDirection = syncDirection.ordinal,
        conflictResolution = conflictResolution.ordinal,
        autoSyncInterval = autoSyncInterval,
        syncOnChapterRead = syncOnChapterRead,
        syncOnMarkComplete = syncOnMarkComplete
    )

    private companion object {
        /** Delay between consecutive tracker syncs in a batch, to respect API rate limits. */
        const val BATCH_SYNC_STAGGER_MS = 350L

        /** Number of [rowLocks] stripes. See [rowLock]. */
        const val STRIPE_COUNT = 64

        /** Spreads (mangaId, trackerId) pairs across stripes instead of clustering by tracker. */
        const val PRIME_MULTIPLIER = 31L
    }
}
