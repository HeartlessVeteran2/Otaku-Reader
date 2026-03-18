package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.MangaCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY `order` ASC")
    fun getCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long
    
    @Update
    suspend fun update(category: CategoryEntity)
    
    @Delete
    suspend fun delete(category: CategoryEntity)
    
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    // Manga-Category relations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMangaCategory(mangaCategory: MangaCategoryEntity)
    
    @Query("DELETE FROM manga_categories WHERE mangaId = :mangaId AND categoryId = :categoryId")
    suspend fun deleteMangaCategory(mangaId: Long, categoryId: Long)
    
    @Query("DELETE FROM manga_categories WHERE mangaId = :mangaId")
    suspend fun deleteMangaCategoriesForManga(mangaId: Long)
    
    @Query("SELECT categoryId FROM manga_categories WHERE mangaId = :mangaId")
    fun getCategoryIdsForManga(mangaId: Long): Flow<List<Long>>

    @Query("SELECT mangaId FROM manga_categories WHERE categoryId = :categoryId")
    fun getMangaIdsByCategoryId(categoryId: Long): Flow<List<Long>>

    @Query("SELECT COALESCE(MAX(`order`), 0) FROM categories")
    suspend fun getMaxCategoryOrder(): Int

    // Hidden category support
    @Query("SELECT * FROM categories WHERE (flags & 1) = 0 ORDER BY `order` ASC")
    fun getVisibleCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE (flags & 1) = 1 ORDER BY `order` ASC")
    fun getHiddenCategories(): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET flags = CASE WHEN (flags & 1) = 1 THEN flags - 1 ELSE flags + 1 END WHERE id = :categoryId")
    suspend fun toggleHiddenFlag(categoryId: Long)

    @Query("UPDATE categories SET flags = CASE WHEN (flags & 2) = 2 THEN flags - 2 ELSE flags + 2 END WHERE id = :categoryId")
    suspend fun toggleNsfwFlag(categoryId: Long)
}
