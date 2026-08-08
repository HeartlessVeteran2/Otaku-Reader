package app.otakureader.data.tracking.repository

import app.otakureader.core.database.dao.TrackEntryDao
import app.otakureader.core.database.dao.TrackerSyncDao
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.tracking.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val dao: TrackEntryDao,
    private val trackerSyncDao: TrackerSyncDao,
) : TrackRepository {

    override fun observeEntriesForManga(mangaId: Long): Flow<List<TrackEntry>> =
        dao.getByMangaId(mangaId).map { entities -> entities.map { it.toDomain() } }

    override fun observeMangaIdsWithTrackEntries(): Flow<Set<Long>> =
        dao.getMangaIdsWithTrackEntries().map { it.toSet() }

    override suspend fun getEntry(mangaId: Long, trackerId: Int): TrackEntry? =
        dao.getByMangaAndTracker(mangaId, trackerId)?.toDomain()

    override suspend fun upsertEntry(entry: TrackEntry) {
        dao.upsert(entry.toEntity())
    }

    /**
     * Removes the track entry *and* the sync-state row that shadows it.
     *
     * `tracker_sync_state` holds no foreign key to `track_entries`, so nothing at the database
     * level ties their lifetimes together — deleting only the entry leaves an orphan row, and
     * `deleteSyncStateForManga` had no production caller at all. The orphan is not inert:
     *
     * - `recordLocalChange` still finds it and keeps marking it PENDING, so `syncAllPending`
     *   keeps retrying a tracker the user deliberately unlinked. Since there is no entry left to
     *   push, every one of those passes now fails, and the error can never clear.
     * - `syncManga` only auto-creates a row when it finds none. Re-linking the same manga to a
     *   *different* remote id would therefore reuse the stale row, carrying the previous link's
     *   `remoteId` and chapter history into the new one — and because `recordLocalChange` takes
     *   `maxOf(existing.localLastChapterRead, chapterRead)`, a high-water mark from the old link
     *   would pin the new one and never come down.
     *
     * Doing it here rather than at the call site keeps the invariant at the single choke point
     * for entry removal, so a future caller cannot forget it.
     */
    override suspend fun deleteEntry(mangaId: Long, trackerId: Int) {
        dao.deleteByMangaAndTracker(mangaId, trackerId)
        trackerSyncDao.deleteSyncState(mangaId, trackerId)
    }
}
