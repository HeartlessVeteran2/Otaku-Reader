package app.otakureader.domain.usecase

import app.otakureader.domain.model.Category
import app.otakureader.domain.repository.CategoryRepository
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetVisibleCategoriesUseCaseTest {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var useCase: GetVisibleCategoriesUseCase

    @Before
    fun setUp() {
        categoryRepository = mockk()
        useCase = GetVisibleCategoriesUseCase(categoryRepository)
    }

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val categories = listOf(
            Category(id = 1L, name = "Action", order = 0, isHidden = false),
            Category(id = 2L, name = "Romance", order = 1, isHidden = false)
        )
        every { categoryRepository.getVisibleCategories() } returns flowOf(categories)

        useCase().test {
            assertEquals(categories, awaitItem())
            awaitComplete()
        }

        verify(exactly = 1) { categoryRepository.getVisibleCategories() }
    }

    @Test
    fun invoke_withEmptyList_emitsEmptyList() = runTest {
        every { categoryRepository.getVisibleCategories() } returns flowOf(emptyList())

        useCase().test {
            assertEquals(emptyList<Category>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun invoke_returnsOnlyVisibleCategories() = runTest {
        val visibleCategories = listOf(
            Category(id = 1L, name = "Action", isHidden = false),
            Category(id = 3L, name = "Sci-Fi", isHidden = false)
        )
        every { categoryRepository.getVisibleCategories() } returns flowOf(visibleCategories)

        useCase().test {
            val result = awaitItem()
            assertTrue(result.all { !it.isHidden })
            assertEquals(2, result.size)
            awaitComplete()
        }
    }
}
