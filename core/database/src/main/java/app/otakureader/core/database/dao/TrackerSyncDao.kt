package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.otakureader.core.database.entity.SyncConfigurationEntity
import app.otakureader.core.database.entity.TrackerSyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerSyncDao {
    // Sync Configuration
    @Query("SELECT * FROM sync_configuration")
    fun getSyncConfigurations(): Flow<List<SyncConfigurationEntity>>

    @Query("SELECT * FROM sync_configuration WHERE trackerId = :trackerId")
    suspend fun getSyncConfiguration(trackerId: Int): SyncConfigurationEntity?

    /**
     * Inserts [config], or updates the existing row for its `trackerId` (#1276).
     *
     * Not `REPLACE`: this table has an `autoGenerate` primary key and a *secondary* unique index,
     * and REPLACE deletes the conflicting row before inserting, so every write handed the row a
     * new id. Same shape as the bug that cost real data in `ChapterDao` (#1254) and `MangaDao`
     * (#1269) — harmless here only because nothing yet references a configuration's id.
     */
    @Transaction
    suspend fun insertSyncConfiguration(config: SyncConfigurationEntity): Long {
        getSyncConfiguration(config.trackerId)?.let { existing ->
            updateSyncConfiguration(config.copy(id = existing.id))
            return existing.id
        }
        val rowId = insertConfigIfAbsent(config)
        if (rowId != -1L) return rowId
        val now = getSyncConfiguration(config.trackerId) ?: return 0L
        updateSyncConfiguration(config.copy(id = now.id))
        return now.id
    }

    @Transaction
    suspend fun insertSyncConfigurations(configs: List<SyncConfigurationEntity>) {
        configs.forEach { insertSyncConfiguration(it) }
    }

    /** Returns the new row id, or -1 when `trackerId` is already taken. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConfigIfAbsent(config: SyncConfigurationEntity): Long

    @Update
    suspend fun updateSyncConfiguration(config: SyncConfigurationEntity)

    @Query("UPDATE sync_configuration SET enabled = :enabled WHERE trackerId = :trackerId")
    suspend fun setSyncEnabled(trackerId: Int, enabled: Boolean)

    // Sync State
    @Query("SELECT * FROM tracker_sync_state WHERE mangaId = :mangaId")
    fun getSyncStateForManga(mangaId: Long): Flow<List<TrackerSyncStateEntity>>

    @Query("SELECT * FROM tracker_sync_state WHERE syncStatus = :status")
    fun getSyncStateByStatus(status: Int): Flow<List<TrackerSyncStateEntity>>

    @Query("SELECT * FROM tracker_sync_state WHERE mangaId = :mangaId AND trackerId = :trackerId")
    suspend fun getSyncState(mangaId: Long, trackerId: Int): TrackerSyncStateEntity?

    /**
     * Creates a sync-state row for `(mangaId, trackerId)` **if one does not already exist**, and
     * returns its id either way. An existing row is left exactly as it is.
     *
     * Create-if-absent, not overwrite, and the distinction is load-bearing. The only caller is the
     * auto-create branch in `TrackerSyncRepositoryImpl.syncManga`, which reads
     * `getSyncState(...) == null` and then inserts — a read-then-act sequence holding nothing
     * across the two steps. Two syncs for the same manga and tracker can both see null. If the
     * loser overwrote, it would replace the winner's row with its own older snapshot: a lost
     * update, which is the exact class of bug #1247 catalogues in this file's callers. Doing
     * nothing on conflict makes the loser harmless.
     *
     * `INSERT OR IGNORE` and then read the id back, rather than `REPLACE` (#1276): REPLACE deletes
     * the conflicting row first, and with an `autoGenerate` key the replacement returns under a
     * **new id** — which matters here more than anywhere else in this DAO, because
     * [updateSyncStatus], [markSyncSuccess] and [markConflict] all address a row *by id* and
     * callers hold that id across a network round-trip.
     *
     * Restore needs the opposite rule and gets its own method; see [insertSyncStates].
     */
    @Transaction
    suspend fun insertSyncState(state: TrackerSyncStateEntity): Long {
        val rowId = insertStateIfAbsent(state)
        if (rowId != -1L) return rowId
        return getSyncState(state.mangaId, state.trackerId)?.id ?: 0L
    }

    /**
     * Writes each state, **overwriting** any existing row for the same `(mangaId, trackerId)`.
     *
     * The restore path, where the backup is meant to win — the opposite rule to
     * [insertSyncState], which is why it is a separate method rather than a loop over it. The
     * overwrite is an `@Update` against the existing row, so the id survives and nothing is
     * deleted.
     */
    @Transaction
    suspend fun insertSyncStates(states: List<TrackerSyncStateEntity>) {
        for (state in states) {
            val existing = getSyncState(state.mangaId, state.trackerId)
            if (existing != null) {
                updateSyncState(state.copy(id = existing.id))
                continue
            }
            if (insertStateIfAbsent(state) == -1L) {
                // Lost the race between the read and the insert: become the update this would
                // have been had it arrived a moment later, so the backup still wins.
                getSyncState(state.mangaId, state.trackerId)?.let { updateSyncState(state.copy(id = it.id)) }
            }
        }
    }

    /** Returns the new row id, or -1 when `(mangaId, trackerId)` is already taken. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStateIfAbsent(state: TrackerSyncStateEntity): Long

    @Update
    suspend fun updateSyncState(state: TrackerSyncStateEntity)

    @Query("DELETE FROM tracker_sync_state WHERE mangaId = :mangaId")
    suspend fun deleteSyncStateForManga(mangaId: Long)

    @Query("DELETE FROM tracker_sync_state WHERE mangaId = :mangaId AND trackerId = :trackerId")
    suspend fun deleteSyncState(mangaId: Long, trackerId: Int)

    // Bulk operations
    @Query("SELECT * FROM tracker_sync_state")
    fun getAllSyncStates(): Flow<List<TrackerSyncStateEntity>>

    @Query("SELECT * FROM tracker_sync_state WHERE syncStatus = 0") // PENDING = 0
    fun getPendingSyncs(): Flow<List<TrackerSyncStateEntity>>

    @Query("UPDATE tracker_sync_state SET syncStatus = :status, lastSyncAttempt = :timestamp WHERE id = :id")
    suspend fun updateSyncAttempt(id: Long, status: Int, timestamp: java.time.Instant)

    @Query("UPDATE tracker_sync_state SET syncStatus = :status, lastSuccessfulSync = :timestamp, syncError = null WHERE id = :id")
    suspend fun markSyncSuccess(id: Long, status: Int, timestamp: java.time.Instant)

    @Query("UPDATE tracker_sync_state SET syncStatus = 3, syncError = :error WHERE id = :id") // CONFLICT = 3
    suspend fun markSyncConflict(id: Long, error: String?)

    /**
     * Records a failure without rewriting the rest of the row.
     *
     * Callers hold a snapshot read before a network request; writing it back with `@Update` would
     * carry its stale local columns over a chapter read that landed during the request.
     */
    @Query(
        "UPDATE tracker_sync_state SET syncStatus = :status, lastSyncAttempt = :timestamp, " +
            "syncError = :error WHERE id = :id"
    )
    suspend fun markSyncError(id: Long, status: Int, timestamp: java.time.Instant, error: String?)
}
