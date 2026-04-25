package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncConfiguration(config: SyncConfigurationEntity): Long

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncState(state: TrackerSyncStateEntity): Long

    @Update
    suspend fun updateSyncState(state: TrackerSyncStateEntity)

    @Query("DELETE FROM tracker_sync_state WHERE mangaId = :mangaId")
    suspend fun deleteSyncStateForManga(mangaId: Long)

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
}
