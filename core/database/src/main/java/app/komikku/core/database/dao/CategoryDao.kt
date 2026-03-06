package app.komikku.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.komikku.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY `order` ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id LIMIT 1")
    suspend fun getCategory(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity): Long

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT c.* FROM category c 
        INNER JOIN manga_category mc ON c.id = mc.category_id 
        WHERE mc.manga_id = :mangaId 
        ORDER BY c.`order` ASC
        """
    )
    fun observeCategoriesForManga(mangaId: Long): Flow<List<CategoryEntity>>
}
