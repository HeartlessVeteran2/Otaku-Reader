package app.otakureader.feature.library.category

import app.otakureader.domain.model.Category
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.usecase.CreateCategoryUseCase
import app.otakureader.domain.usecase.DeleteCategoryUseCase
import app.otakureader.domain.usecase.ToggleCategoryHiddenUseCase
import app.otakureader.domain.usecase.ToggleCategoryNsfwUseCase
import app.otakureader.domain.usecase.UpdateCategoryUseCase
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var createCategoryUseCase: CreateCategoryUseCase
    private lateinit var updateCategoryUseCase: UpdateCategoryUseCase
    private lateinit var deleteCategoryUseCase: DeleteCategoryUseCase
    private lateinit var toggleCategoryHiddenUseCase: ToggleCategoryHiddenUseCase
    private lateinit var toggleCategoryNsfwUseCase: ToggleCategoryNsfwUseCase

    private val sampleCategories = listOf(
        Category(id = 1L, name = "Romance", order = 0, isHidden = false, isNsfw = false),
        Category(id = 2L, name = "Action", order = 1, isHidden = true, isNsfw = false),
        Category(id = 3L, name = "Adult", order = 2, isHidden = false, isNsfw = true),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        categoryRepository = mockk()
        createCategoryUseCase = mockk()
        updateCategoryUseCase = mockk()
        deleteCategoryUseCase = mockk()
        toggleCategoryHiddenUseCase = mockk()
        toggleCategoryNsfwUseCase = mockk()

        every { categoryRepository.getMangaIdsByCategoryId(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CategoryManagementViewModel {
        return CategoryManagementViewModel(
            categoryRepository,
            createCategoryUseCase,
            updateCategoryUseCase,
            deleteCategoryUseCase,
            toggleCategoryHiddenUseCase,
            toggleCategoryNsfwUseCase,
        )
    }

    @Test
    fun init_loadsCategories() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(sampleCategories)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.categories.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun init_sortsCategoriesAlphabetically() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(sampleCategories)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val names = viewModel.state.value.categories.map { it.name }
        assertEquals(listOf("Action", "Adult", "Romance"), names)
    }

    @Test
    fun init_mapsIsHiddenFromDomainModel() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(sampleCategories)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val actionItem = viewModel.state.value.categories.first { it.id == 2L }
        assertTrue(actionItem.isHidden)
        val romanceItem = viewModel.state.value.categories.first { it.id == 1L }
        assertFalse(romanceItem.isHidden)
    }

    @Test
    fun init_mapsIsNsfwFromDomainModel() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(sampleCategories)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val adultItem = viewModel.state.value.categories.first { it.id == 3L }
        assertTrue(adultItem.isNsfw)
        val romanceItem = viewModel.state.value.categories.first { it.id == 1L }
        assertFalse(romanceItem.isNsfw)
    }

    @Test
    fun init_populatesMangaCountFromRepository() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(listOf(sampleCategories[0]))
        every { categoryRepository.getMangaIdsByCategoryId(1L) } returns flowOf(listOf(10L, 20L))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.categories.first().mangaCount)
    }

    @Test
    fun onEvent_CreateCategory_callsUseCaseAndEmitsEffects() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        coEvery { createCategoryUseCase("New Cat") } returns 10L

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(CategoryEvent.CreateCategory("New Cat"))
            testDispatcher.scheduler.advanceUntilIdle()

            val first = awaitItem()
            assertTrue(first is CategoryEffect.DismissDialog)
            val second = awaitItem()
            assertTrue(second is CategoryEffect.ShowSnackbar)
            assertEquals("Category created", (second as CategoryEffect.ShowSnackbar).message)
        }

        coVerify(exactly = 1) { createCategoryUseCase("New Cat") }
    }

    @Test
    fun onEvent_UpdateCategory_callsUseCaseAndEmitsEffects() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        coEvery { updateCategoryUseCase(1L, "Updated") } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(CategoryEvent.UpdateCategory(1L, "Updated"))
            testDispatcher.scheduler.advanceUntilIdle()

            val first = awaitItem()
            assertTrue(first is CategoryEffect.DismissDialog)
            val second = awaitItem()
            assertEquals("Category updated", (second as CategoryEffect.ShowSnackbar).message)
        }

        coVerify(exactly = 1) { updateCategoryUseCase(1L, "Updated") }
    }

    @Test
    fun onEvent_DeleteCategory_callsUseCaseAndEmitsSnackbar() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        coEvery { deleteCategoryUseCase(2L) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(CategoryEvent.DeleteCategory(2L))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertEquals("Category deleted", (effect as CategoryEffect.ShowSnackbar).message)
        }

        coVerify(exactly = 1) { deleteCategoryUseCase(2L) }
    }

    @Test
    fun onEvent_ToggleHidden_callsUseCase() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        coEvery { toggleCategoryHiddenUseCase(1L) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.ToggleHidden(1L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { toggleCategoryHiddenUseCase(1L) }
    }

    @Test
    fun onEvent_ToggleNsfw_callsUseCase() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        coEvery { toggleCategoryNsfwUseCase(3L) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.ToggleNsfw(3L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { toggleCategoryNsfwUseCase(3L) }
    }

    @Test
    fun onEvent_CreateCategory_onError_emitsErrorSnackbar() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        coEvery { createCategoryUseCase(any()) } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(CategoryEvent.CreateCategory("Fail"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue((effect as CategoryEffect.ShowSnackbar).message.startsWith("Failed to create category"))
        }
    }

    @Test
    fun init_withEmptyCategories_emitsEmptyList() = runTest {
        every { categoryRepository.getCategories() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.categories.isEmpty())
        assertFalse(viewModel.state.value.isLoading)
    }
}
