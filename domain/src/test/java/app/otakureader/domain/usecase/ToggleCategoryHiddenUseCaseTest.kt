package app.otakureader.domain.usecase

import app.otakureader.domain.repository.CategoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ToggleCategoryHiddenUseCaseTest {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var useCase: ToggleCategoryHiddenUseCase

    @Before
    fun setUp() {
        categoryRepository = mockk()
        useCase = ToggleCategoryHiddenUseCase(categoryRepository)
    }

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val categoryId = 5L
        coEvery { categoryRepository.toggleCategoryHidden(categoryId) } returns Unit

        useCase(categoryId)

        coVerify(exactly = 1) { categoryRepository.toggleCategoryHidden(categoryId) }
    }

    @Test
    fun invoke_withDifferentId_passesCorrectId() = runTest {
        val categoryId = 42L
        coEvery { categoryRepository.toggleCategoryHidden(categoryId) } returns Unit

        useCase(categoryId)

        coVerify(exactly = 1) { categoryRepository.toggleCategoryHidden(42L) }
    }
}
