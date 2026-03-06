package app.komikku.data.repository

import app.komikku.core.database.dao.CategoryDao
import app.komikku.core.database.dao.MangaCategoryDao
import app.komikku.core.database.entity.CategoryEntity
import app.komikku.core.database.entity.MangaCategoryEntity
import app.komikku.domain.model.Category
import app.komikku.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val mangaCategoryDao: MangaCategoryDao
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeCategories().map { list -> list.map { it.toDomain() } }

    override suspend fun getCategory(id: Long): Category? =
        categoryDao.getCategory(id)?.toDomain()

    override suspend fun upsertCategory(category: Category): Long =
        categoryDao.upsert(category.toEntity())

    override suspend fun deleteCategory(id: Long) =
        categoryDao.delete(id)

    override suspend fun reorderCategories(categories: List<Category>) {
        categories.forEachIndexed { index, category ->
            categoryDao.upsert(category.copy(order = index).toEntity())
        }
    }

    override suspend fun addMangaToCategory(mangaId: Long, categoryId: Long) =
        mangaCategoryDao.upsert(MangaCategoryEntity(mangaId, categoryId))

    override suspend fun removeMangaFromCategory(mangaId: Long, categoryId: Long) =
        mangaCategoryDao.delete(mangaId, categoryId)

    override fun observeCategoriesForManga(mangaId: Long): Flow<List<Category>> =
        categoryDao.observeCategoriesForManga(mangaId).map { list -> list.map { it.toDomain() } }

    private fun CategoryEntity.toDomain() = Category(id = id, name = name, order = order, flags = flags)
    private fun Category.toEntity() = CategoryEntity(id = id, name = name, order = order, flags = flags)
}
