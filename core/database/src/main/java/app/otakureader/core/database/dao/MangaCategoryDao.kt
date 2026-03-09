package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.otakureader.core.database.entity.MangaCategoryEntity

@Dao
interface MangaCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mangaCategory: MangaCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mangaCategories: List<MangaCategoryEntity>)

    @Query("DELETE FROM manga_categories WHERE mangaId = :mangaId AND categoryId = :categoryId")
    suspend fun delete(mangaId: Long, categoryId: Long)

    @Query("DELETE FROM manga_categories WHERE mangaId = :mangaId")
    suspend fun deleteAllForManga(mangaId: Long)
}
