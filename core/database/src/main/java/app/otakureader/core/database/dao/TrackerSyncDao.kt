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
     * Inserts [state], or updates the existing row for its `(mangaId, trackerId)` pair (#1276).
     *
     * This is the one of the three where the id genuinely matters. `updateSyncStatus`,
     * `markSyncSuccess` and `markConflict` all address a row **by id**, and callers hold that id
     * across a network round-trip — so a `REPLACE` landing in between would leave those updates
     * silently affecting zero rows. Today the call sites' own locking makes that unreachable, but
     * that is discipline at the call site standing in for a property of the table.
     *
     * Overwrite semantics are kept deliberately: a restore has to win over whatever is already
     * there, and [insertSyncStates] is the path it uses.
     */
    @Transaction
    suspend fun insertSyncState(state: TrackerSyncStateEntity): Long {
        getSyncState(state.mangaId, state.trackerId)?.let { existing ->
            updateSyncState(state.copy(id = existing.id))
            return existing.id
        }
        val rowId = insertStateIfAbsent(state)
        if (rowId != -1L) return rowId
        val now = getSyncState(state.mangaId, state.trackerId) ?: return 0L
        updateSyncState(state.copy(id = now.id))
        return now.id
    }

    @Transaction
    suspend fun insertSyncStates(states: List<TrackerSyncStateEntity>) {
        states.forEach { insertSyncState(it) }
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
