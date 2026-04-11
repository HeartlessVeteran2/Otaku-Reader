package app.otakureader.domain.usecase

import app.otakureader.domain.repository.CategoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ToggleCategoryNsfwUseCaseTest {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var useCase: ToggleCategoryNsfwUseCase

    @Before
    fun setUp() {
        categoryRepository = mockk()
        useCase = ToggleCategoryNsfwUseCase(categoryRepository)
    }

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val categoryId = 3L
        coEvery { categoryRepository.toggleCategoryNsfw(categoryId) } returns Unit

        useCase(categoryId)

        coVerify(exactly = 1) { categoryRepository.toggleCategoryNsfw(categoryId) }
    }

    @Test
    fun invoke_withDifferentId_passesCorrectId() = runTest {
        val categoryId = 77L
        coEvery { categoryRepository.toggleCategoryNsfw(categoryId) } returns Unit

        useCase(categoryId)

        coVerify(exactly = 1) { categoryRepository.toggleCategoryNsfw(77L) }
    }
}
