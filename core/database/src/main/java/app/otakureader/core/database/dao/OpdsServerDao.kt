package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.otakureader.core.database.entity.OpdsServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpdsServerDao {

    @Query("SELECT * FROM opds_servers ORDER BY name ASC")
    fun getAll(): Flow<List<OpdsServerEntity>>

    @Query("SELECT * FROM opds_servers WHERE id = :id")
    suspend fun getById(id: Long): OpdsServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: OpdsServerEntity): Long

    @Update
    suspend fun update(server: OpdsServerEntity)

    @Query("DELETE FROM opds_servers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
