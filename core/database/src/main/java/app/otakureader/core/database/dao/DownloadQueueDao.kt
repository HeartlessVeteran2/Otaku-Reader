package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.otakureader.core.database.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {

    @Query("SELECT * FROM download_queue ORDER BY priority ASC, added_at ASC")
    fun getAll(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue ORDER BY priority ASC, added_at ASC")
    suspend fun getAllOnce(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE chapter_id = :chapterId LIMIT 1")
    suspend fun getByChapterId(chapterId: Long): DownloadQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadQueueEntity)

    @Query("UPDATE download_queue SET status = :status WHERE chapter_id = :chapterId")
    suspend fun updateStatus(chapterId: Long, status: String)

    @Query("UPDATE download_queue SET priority = :priority WHERE chapter_id = :chapterId")
    suspend fun updatePriority(chapterId: Long, priority: Int)

    /** Batched variant of [updatePriority] for reordering the whole queue in one transaction. */
    @Transaction
    suspend fun updatePriorities(updates: Map<Long, Int>) {
        for ((chapterId, priority) in updates) {
            updatePriority(chapterId, priority)
        }
    }

    @Query("UPDATE download_queue SET page_urls_json = :pageUrlsJson WHERE chapter_id = :chapterId")
    suspend fun updatePageUrls(chapterId: Long, pageUrlsJson: String)

    @Query("DELETE FROM download_queue WHERE chapter_id = :chapterId")
    suspend fun delete(chapterId: Long)

    @Query("DELETE FROM download_queue")
    suspend fun deleteAll()
}
