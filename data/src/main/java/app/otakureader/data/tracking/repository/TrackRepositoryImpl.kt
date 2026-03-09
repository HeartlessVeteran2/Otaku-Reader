package app.otakureader.data.tracking.repository

import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.tracking.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [TrackRepository].
 *
 * A production implementation would persist entries to a Room database table.
 * This implementation is sufficient for the current stage of the project where
 * the tracking database schema has not yet been finalised.
 */
@Singleton
class TrackRepositoryImpl @Inject constructor() : TrackRepository {

    private val entries = MutableStateFlow<List<TrackEntry>>(emptyList())

    override fun observeEntriesForManga(mangaId: Long): Flow<List<TrackEntry>> =
        entries.map { list -> list.filter { it.mangaId == mangaId } }

    override suspend fun getEntry(trackerId: Int, remoteId: Long): TrackEntry? =
        entries.value.firstOrNull { it.trackerId == trackerId && it.remoteId == remoteId }

    override suspend fun upsertEntry(entry: TrackEntry) {
        entries.update { list ->
            val existing = list.indexOfFirst { it.trackerId == entry.trackerId && it.remoteId == entry.remoteId }
            if (existing >= 0) list.toMutableList().apply { set(existing, entry) }
            else list + entry
        }
    }

    override suspend fun deleteEntry(trackerId: Int, remoteId: Long) {
        entries.update { list ->
            list.filter { !(it.trackerId == trackerId && it.remoteId == remoteId) }
        }
    }
}
