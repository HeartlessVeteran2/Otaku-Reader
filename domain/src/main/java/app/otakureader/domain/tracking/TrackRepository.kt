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

    /** Retrieve a single entry by tracker + remote ID. */
    suspend fun getEntry(trackerId: Int, remoteId: Long): TrackEntry?

    /** Insert or replace a track entry. */
    suspend fun upsertEntry(entry: TrackEntry)

    /** Remove an entry from local storage. */
    suspend fun deleteEntry(trackerId: Int, remoteId: Long)
}
