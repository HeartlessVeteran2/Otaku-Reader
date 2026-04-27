package app.otakureader.domain.usecase

import app.otakureader.domain.repository.CategoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CreateCategoryUseCaseTest {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var useCase: CreateCategoryUseCase

    @Before
    fun setUp() {
        categoryRepository = mockk()
        useCase = CreateCategoryUseCase(categoryRepository)
    }

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val name = "Action"
        coEvery { categoryRepository.createCategory(name) } returns 1L

        val result = useCase(name)

        coVerify(exactly = 1) { categoryRepository.createCategory(name) }
        assertEquals(1L, result)
    }

    @Test
    fun invoke_trimsWhitespaceBeforeCreating() = runTest {
        coEvery { categoryRepository.createCategory("Romance") } returns 2L

        val result = useCase("  Romance  ")

        coVerify(exactly = 1) { categoryRepository.createCategory("Romance") }
        assertEquals(2L, result)
    }

    @Test
    fun invoke_returnsIdFromRepository() = runTest {
        coEvery { categoryRepository.createCategory("Sci-Fi") } returns 42L

        val result = useCase("Sci-Fi")

        assertEquals(42L, result)
    }
}
