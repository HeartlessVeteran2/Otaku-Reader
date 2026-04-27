package app.otakureader.domain.usecase

import app.otakureader.domain.model.Category
import app.otakureader.domain.repository.CategoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UpdateCategoryUseCaseTest {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var useCase: UpdateCategoryUseCase

    @Before
    fun setUp() {
        categoryRepository = mockk()
        useCase = UpdateCategoryUseCase(categoryRepository)
    }

    @Test
    fun invoke_fetchesCategoryThenUpdatesName() = runTest {
        val existing = Category(id = 1L, name = "Old Name", order = 0)
        val updated = existing.copy(name = "New Name")
        coEvery { categoryRepository.getCategoryById(1L) } returns existing
        coEvery { categoryRepository.updateCategory(updated) } returns Unit

        useCase(1L, "New Name")

        coVerify(exactly = 1) { categoryRepository.getCategoryById(1L) }
        coVerify(exactly = 1) { categoryRepository.updateCategory(updated) }
    }

    @Test
    fun invoke_trimsWhitespaceFromName() = runTest {
        val existing = Category(id = 2L, name = "Old", order = 1)
        val updated = existing.copy(name = "Trimmed")
        coEvery { categoryRepository.getCategoryById(2L) } returns existing
        coEvery { categoryRepository.updateCategory(updated) } returns Unit

        useCase(2L, "  Trimmed  ")

        coVerify(exactly = 1) { categoryRepository.updateCategory(updated) }
    }

    @Test
    fun invoke_whenCategoryNotFound_doesNotCallUpdate() = runTest {
        coEvery { categoryRepository.getCategoryById(99L) } returns null

        useCase(99L, "New Name")

        coVerify(exactly = 0) { categoryRepository.updateCategory(any()) }
    }
}
