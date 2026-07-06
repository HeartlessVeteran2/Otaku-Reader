package app.otakureader.domain.tracking

import app.otakureader.domain.model.TrackEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for persisting and retrieving [TrackEntry] objects.
 * Implementations live in the data layer.
 */
interface TrackRepository {
    /** Observe all entries for a given local manga. */
    fun observeEntriesForManga(mangaId: Long): Flow<List<TrackEntry>>

    /** Observe the set of manga IDs that have at least one tracker entry across all trackers. */
    fun observeMangaIdsWithTrackEntries(): Flow<Set<Long>>

    /** Retrieve a single entry by the unique (manga, tracker) key. */
    suspend fun getEntry(mangaId: Long, trackerId: Int): TrackEntry?

    /** Insert or replace a track entry. */
    suspend fun upsertEntry(entry: TrackEntry)

    /** Remove an entry from local storage by the unique (manga, tracker) key. */
    suspend fun deleteEntry(mangaId: Long, trackerId: Int)
}
