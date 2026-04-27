package app.otakureader.domain.usecase

import app.otakureader.domain.repository.CategoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeleteCategoryUseCaseTest {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var useCase: DeleteCategoryUseCase

    @Before
    fun setUp() {
        categoryRepository = mockk()
        useCase = DeleteCategoryUseCase(categoryRepository)
    }

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val categoryId = 5L
        coEvery { categoryRepository.deleteCategory(categoryId) } returns Unit

        useCase(categoryId)

        coVerify(exactly = 1) { categoryRepository.deleteCategory(categoryId) }
    }

    @Test
    fun invoke_withDifferentId_passesCorrectId() = runTest {
        val categoryId = 42L
        coEvery { categoryRepository.deleteCategory(categoryId) } returns Unit

        useCase(categoryId)

        coVerify(exactly = 1) { categoryRepository.deleteCategory(42L) }
    }
}
