package app.komikku.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.komikku.core.database.entity.MangaCategoryEntity

@Dao
interface MangaCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mangaCategory: MangaCategoryEntity)

    @Query("DELETE FROM manga_category WHERE manga_id = :mangaId AND category_id = :categoryId")
    suspend fun delete(mangaId: Long, categoryId: Long)

    @Query("DELETE FROM manga_category WHERE manga_id = :mangaId")
    suspend fun deleteAllForManga(mangaId: Long)
}
