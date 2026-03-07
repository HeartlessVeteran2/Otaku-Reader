package app.komikku.data.repository

import app.komikku.core.database.dao.CategoryDao
import app.komikku.core.database.dao.MangaCategoryDao
import app.komikku.core.database.entity.CategoryEntity
import app.komikku.core.database.entity.MangaCategoryEntity
import app.komikku.domain.model.Category
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CategoryRepositoryImplTest {

    private lateinit var categoryDao: CategoryDao
    private lateinit var mangaCategoryDao: MangaCategoryDao
    private lateinit var repository: CategoryRepositoryImpl

    @Before
    fun setup() {
        categoryDao = mockk()
        mangaCategoryDao = mockk()
        repository = CategoryRepositoryImpl(categoryDao, mangaCategoryDao)
    }

    @Test
    fun `toEntity maps Category to CategoryEntity correctly`() = runTest {
        // Arrange
        val category = Category(
            id = 1L,
            name = "Test Category",
            order = 2,
            flags = 3L
        )
        val expectedEntity = CategoryEntity(
            id = 1L,
            name = "Test Category",
            order = 2,
            flags = 3L
        )

        coEvery { categoryDao.upsert(any()) } returns 1L

        // Act
        repository.upsertCategory(category)

        // Assert
        coVerify { categoryDao.upsert(expectedEntity) }
    }

    @Test
    fun `reorderCategories uses toEntity correctly`() = runTest {
        // Arrange
        val categories = listOf(
            Category(id = 1L, name = "Cat 1", order = 5, flags = 0L),
            Category(id = 2L, name = "Cat 2", order = 2, flags = 1L)
        )

        val expectedEntity1 = CategoryEntity(id = 1L, name = "Cat 1", order = 0, flags = 0L)
        val expectedEntity2 = CategoryEntity(id = 2L, name = "Cat 2", order = 1, flags = 1L)

        coEvery { categoryDao.upsert(any()) } returns 1L

        // Act
        repository.reorderCategories(categories)

        // Assert
        coVerify(exactly = 1) { categoryDao.upsert(expectedEntity1) }
        coVerify(exactly = 1) { categoryDao.upsert(expectedEntity2) }
    }

    @Test
    fun `deleteCategory passes correct id to DAO`() = runTest {
        // Arrange
        val categoryId = 42L
        coEvery { categoryDao.delete(categoryId) } returns Unit

        // Act
        repository.deleteCategory(categoryId)

        // Assert
        coVerify(exactly = 1) { categoryDao.delete(categoryId) }
    }
}
