package app.komikku.domain.repository

import app.komikku.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for library category management.
 */
interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun getCategory(id: Long): Category?
    suspend fun upsertCategory(category: Category): Long
    suspend fun deleteCategory(id: Long)
    suspend fun reorderCategories(categories: List<Category>)
    suspend fun addMangaToCategory(mangaId: Long, categoryId: Long)
    suspend fun removeMangaFromCategory(mangaId: Long, categoryId: Long)
    fun observeCategoriesForManga(mangaId: Long): Flow<List<Category>>
}
