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
import app.otakureader.domain.model.TrackerSyncState
import app.otakureader.domain.repository.TrackerSyncRepository
import app.otakureader.domain.tracking.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

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

    override suspend fun recordLocalChange(
        mangaId: Long,
        trackerId: Int,
        chapterRead: Float,
        status: MangaStatus
    ) {
        val now = Instant.now()
        val existing = trackerSyncDao.getSyncState(mangaId, trackerId)
        if (existing != null) {
            trackerSyncDao.updateSyncState(
                existing.copy(
                    localLastChapterRead = chapterRead,
                    localStatus = status.ordinal,
                    localLastModified = now,
                    syncStatus = SyncStatus.PENDING.ordinal
                )
            )
        }
        // If no sync state exists, local changes will be captured on first sync
    }

    // ── Sync Operations ────────────────────────────────────────────────────

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
            val remoteChanged = lastSync == null ||
                (syncState.remoteLastModified != null && syncState.remoteLastModified > lastSync)

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
                    ConflictResolution.ASK -> throw IllegalStateException(
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
                val localEntry = trackRepository.getEntry(trackerId, remoteId)
                if (localEntry != null) {
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
                }
            } else {
                val localEntry = trackRepository.getEntry(trackerId, remoteId)
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

            TrackerSyncRepository.SyncResult(true, "Sync successful")
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

        pending.forEach { state ->
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
            val localEntry = trackRepository.getEntry(trackerId, remoteId) ?: return
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
                val localEntry = trackRepository.getEntry(trackerId, remoteId)
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

        val localEntry = trackRepository.getEntry(trackerId, remoteId)
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

            val localEntry = trackRepository.getEntry(trackerId, remoteId)
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
}
