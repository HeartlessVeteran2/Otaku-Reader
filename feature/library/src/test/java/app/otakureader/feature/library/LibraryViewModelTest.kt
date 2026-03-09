package app.otakureader.feature.library

import android.content.Context
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.domain.model.Manga
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.ToggleFavoriteMangaUseCase
import app.cash.turbine.test
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getLibraryManga: GetLibraryMangaUseCase
    private lateinit var toggleFavoriteManga: ToggleFavoriteMangaUseCase
    private lateinit var context: Context
    private lateinit var libraryPreferences: LibraryPreferences

    private val sampleMangas = listOf(
        Manga(id = 1L, sourceId = 1L, url = "/m/1", title = "Naruto", favorite = true, unreadCount = 3),
        Manga(id = 2L, sourceId = 1L, url = "/m/2", title = "Bleach", favorite = true, unreadCount = 0),
        Manga(id = 3L, sourceId = 1L, url = "/m/3", title = "One Piece", favorite = true, unreadCount = 7)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getLibraryManga = mockk()
        toggleFavoriteManga = mockk()
        context = mockk()
        libraryPreferences = mockk()

        every { libraryPreferences.gridSize } returns flowOf(2)
        every { libraryPreferences.showBadges } returns flowOf(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(context, getLibraryManga, toggleFavoriteManga, libraryPreferences)
    }

    @Test
    fun init_loadsLibraryOnCreation() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.mangaList.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun init_setsLoadingFalseAfterLoad() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun onEvent_Refresh_reloadsLibrary() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.mangaList.size)
    }

    @Test
    fun onEvent_OnSearchQueryChange_updatesSearchQuery() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.OnSearchQueryChange("Naruto"))

        assertEquals("Naruto", viewModel.state.value.searchQuery)
    }

    @Test
    fun onEvent_OnCategorySelected_updatesCategoryFilter() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        viewModel.onEvent(LibraryEvent.OnCategorySelected(5L))

        assertEquals(5L, viewModel.state.value.selectedCategory)
    }

    @Test
    fun onEvent_OnCategorySelected_withNull_clearsCategoryFilter() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        viewModel.onEvent(LibraryEvent.OnCategorySelected(5L))
        viewModel.onEvent(LibraryEvent.OnCategorySelected(null))

        assertNull(viewModel.state.value.selectedCategory)
    }

    @Test
    fun onEvent_OnMangaLongClick_selectsManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.OnMangaLongClick(1L))

        assertTrue(viewModel.state.value.selectedManga.contains(1L))
    }

    @Test
    fun onEvent_OnMangaLongClick_twice_deselectsManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.OnMangaLongClick(1L))
        viewModel.onEvent(LibraryEvent.OnMangaLongClick(1L))

        assertFalse(viewModel.state.value.selectedManga.contains(1L))
    }

    @Test
    fun onEvent_ClearSelection_removesAllSelections() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.OnMangaLongClick(1L))
        viewModel.onEvent(LibraryEvent.OnMangaLongClick(2L))
        viewModel.onEvent(LibraryEvent.ClearSelection)

        assertTrue(viewModel.state.value.selectedManga.isEmpty())
    }

    @Test
    fun onEvent_ToggleFavorite_callsUseCase() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)
        coEvery { toggleFavoriteManga(any()) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.ToggleFavorite(1L))
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify { toggleFavoriteManga(1L) }
    }

    @Test
    fun onEvent_OnMangaClick_withSelection_togglesSelection() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // First long-click to start selection mode
        viewModel.onEvent(LibraryEvent.OnMangaLongClick(2L))
        // Then click another item — should add to selection
        viewModel.onEvent(LibraryEvent.OnMangaClick(1L))

        assertTrue(viewModel.state.value.selectedManga.contains(1L))
        assertTrue(viewModel.state.value.selectedManga.contains(2L))
    }

    @Test
    fun onEvent_OnMangaClick_withoutSelection_emitsNavigateEffect() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LibraryEvent.OnMangaClick(1L))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is LibraryEffect.NavigateToManga)
            assertEquals(1L, (effect as LibraryEffect.NavigateToManga).mangaId)
        }
    }

    @Test
    fun loadLibrary_mapsUnreadCountFromManga() = runTest {
        every { getLibraryManga() } returns flowOf(listOf(sampleMangas[0]))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val item = viewModel.state.value.mangaList.first()
        assertEquals(sampleMangas[0].unreadCount, item.unreadCount)
    }

    @Test
    fun loadLibrary_mapsIsFavoriteFromManga() = runTest {
        every { getLibraryManga() } returns flowOf(listOf(sampleMangas[0]))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val item = viewModel.state.value.mangaList.first()
        assertTrue(item.isFavorite)
    }

    @Test
    fun loadLibrary_onError_setsErrorState() = runTest {
        every { getLibraryManga() } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("Database error")
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Database error", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }
}

