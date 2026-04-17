package app.otakureader.feature.library

import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.RecommendationResult
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.domain.usecase.DismissRecommendationUseCase
import app.otakureader.domain.usecase.GetForYouRecommendationsUseCase
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.RefreshRecommendationsUseCase
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
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var trackRepository: TrackRepository
    private lateinit var getForYouRecommendations: GetForYouRecommendationsUseCase
    private lateinit var refreshRecommendations: RefreshRecommendationsUseCase
    private lateinit var dismissRecommendation: DismissRecommendationUseCase
    private lateinit var categoryDao: CategoryDao

    private val sampleMangas = listOf(
        Manga(id = 1L, sourceId = 10L, url = "/m/1", title = "Naruto", favorite = true, unreadCount = 3, lastRead = 1000L, status = MangaStatus.ONGOING),
        Manga(id = 2L, sourceId = 20L, url = "/m/2", title = "Bleach", favorite = true, unreadCount = 0, lastRead = 2000L, status = MangaStatus.COMPLETED),
        Manga(id = 3L, sourceId = 10L, url = "/m/3", title = "One Piece", favorite = true, unreadCount = 7, lastRead = null, status = MangaStatus.ONGOING)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getLibraryManga = mockk()
        toggleFavoriteManga = mockk()
        libraryPreferences = mockk {
            every { gridSize } returns flowOf(3)
            every { showBadges } returns flowOf(true)
            every { librarySortMode } returns flowOf(0)
            every { libraryFilterMode } returns flowOf(0)
            every { libraryFilterSourceId } returns flowOf(null)
        }
        generalPreferences = mockk {
            every { showNsfwContent } returns flowOf(true)
            every { lastUpdatesViewedAt } returns flowOf(0L)
        }
        chapterRepository = mockk {
            every { countNewUpdatesSince(any()) } returns flowOf(0)
        }
        downloadRepository = mockk {
            coEvery { hasMangaDownloads(any(), any()) } returns false
        }
        trackRepository = mockk {
            every { observeEntriesForManga(any()) } returns flowOf(emptyList())
        }
        getForYouRecommendations = mockk {
            coEvery { this@mockk.invoke(any()) } returns Result.success(emptyList())
        }
        refreshRecommendations = mockk {
            coEvery { this@mockk.invoke() } returns Result.success(RecommendationResult(recommendations = emptyList()))
        }
        dismissRecommendation = mockk {
            coEvery { this@mockk.invoke(any()) } returns Unit
        }
        categoryDao = mockk(relaxed = true) {
            every { getCategories() } returns flowOf(emptyList())
            every { getMangaIdsByCategoryId(any()) } returns flowOf(emptyList())
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(
            getLibraryManga,
            toggleFavoriteManga,
            libraryPreferences,
            generalPreferences,
            chapterRepository,
            downloadRepository,
            trackRepository,
            categoryDao,
            getForYouRecommendations,
            refreshRecommendations,
            dismissRecommendation
        )
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

    // --- Sort mode tests ---

    @Test
    fun sortMode_ALPHABETICAL_sortsByTitle() = runTest {
        every { libraryPreferences.librarySortMode } returns flowOf(LibrarySortMode.ALPHABETICAL.ordinal)
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val titles = viewModel.state.value.mangaList.map { it.title }
        assertEquals(listOf("Bleach", "Naruto", "One Piece"), titles)
    }

    @Test
    fun sortMode_LAST_READ_sortsByLastReadDescending() = runTest {
        every { libraryPreferences.librarySortMode } returns flowOf(LibrarySortMode.LAST_READ.ordinal)
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Bleach(lastRead=2000) > Naruto(lastRead=1000) > One Piece(lastRead=null=0)
        val ids = viewModel.state.value.mangaList.map { it.id }
        assertEquals(listOf(2L, 1L, 3L), ids)
    }

    @Test
    fun sortMode_UNREAD_COUNT_sortsByUnreadDescending() = runTest {
        every { libraryPreferences.librarySortMode } returns flowOf(LibrarySortMode.UNREAD_COUNT.ordinal)
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // One Piece(7) > Naruto(3) > Bleach(0)
        val ids = viewModel.state.value.mangaList.map { it.id }
        assertEquals(listOf(3L, 1L, 2L), ids)
    }

    @Test
    fun sortMode_SOURCE_sortsBySourceId() = runTest {
        every { libraryPreferences.librarySortMode } returns flowOf(LibrarySortMode.SOURCE.ordinal)
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // sourceId 10 (Naruto, One Piece) then 20 (Bleach)
        val sourceIds = viewModel.state.value.mangaList.map { it.sourceId }
        assertTrue(sourceIds.first() < sourceIds.last())
    }

    // --- Filter mode tests ---

    @Test
    fun filterMode_UNREAD_showsOnlyUnreadManga() = runTest {
        every { libraryPreferences.libraryFilterMode } returns flowOf(LibraryFilterMode.UNREAD.ordinal)
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Only Naruto(3) and One Piece(7) have unread > 0
        assertEquals(2, viewModel.state.value.mangaList.size)
        assertTrue(viewModel.state.value.mangaList.none { it.id == 2L })
    }

    @Test
    fun filterMode_COMPLETED_showsOnlyCompletedManga() = runTest {
        every { libraryPreferences.libraryFilterMode } returns flowOf(LibraryFilterMode.COMPLETED.ordinal)
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Only Bleach has status COMPLETED
        assertEquals(1, viewModel.state.value.mangaList.size)
        assertEquals(2L, viewModel.state.value.mangaList.first().id)
    }

    @Test
    fun filterMode_ALL_showsAllManga() = runTest {
        every { libraryPreferences.libraryFilterMode } returns flowOf(LibraryFilterMode.ALL.ordinal)
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.mangaList.size)
    }

    // --- NSFW filter test ---

    @Test
    fun nsfw_filterAppliedWithoutCrash_whenShowNsfwFalse() = runTest {
        // NOTE: isNsfw is always false in toLibraryItem() because source/extension NSFW metadata
        // is not yet exposed through the Manga domain model. This test verifies the NSFW filter
        // code path runs without errors and state remains consistent.
        // When source NSFW metadata is wired into the Manga model, update this test to assert
        // that NSFW items are hidden (expected size would drop from 4 to 3).
        every { generalPreferences.showNsfwContent } returns flowOf(false)
        val nsfwManga = Manga(id = 99L, sourceId = 5L, url = "/m/99", title = "Adult Title", favorite = true)
        every { getLibraryManga() } returns flowOf(sampleMangas + nsfwManga)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(4, viewModel.state.value.mangaList.size)
    }

    @Test
    fun filterSource_filtersToSpecificSource() = runTest {
        every { libraryPreferences.libraryFilterSourceId } returns flowOf(10L)
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Only Naruto and One Piece have sourceId 10
        assertEquals(2, viewModel.state.value.mangaList.size)
        assertTrue(viewModel.state.value.mangaList.all { it.sourceId == 10L })
    }

    // --- Badge counter (newUpdatesCount) ---

    @Test
    fun newUpdatesCount_reflectsChapterRepositoryCount() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())
        every { chapterRepository.countNewUpdatesSince(0L) } returns flowOf(5)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.state.value.newUpdatesCount)
    }

    @Test
    fun newUpdatesCount_usesLastUpdatesViewedAt_asWindowStart() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())
        val lastViewed = 9_000_000L
        every { generalPreferences.lastUpdatesViewedAt } returns flowOf(lastViewed)
        every { chapterRepository.countNewUpdatesSince(lastViewed) } returns flowOf(3)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.newUpdatesCount)
    }
}
