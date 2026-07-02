package app.otakureader.feature.browse

import app.cash.turbine.test
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.model.FeedSavedSearch
import app.otakureader.domain.repository.FeedRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.library.AddMangaToLibraryUseCase
import app.otakureader.domain.usecase.ToggleFavoriteMangaUseCase
import app.otakureader.domain.usecase.source.GetLatestUpdatesUseCase
import app.otakureader.domain.usecase.source.GetPopularMangaUseCase
import app.otakureader.domain.usecase.source.GetSourceFiltersUseCase
import app.otakureader.domain.usecase.source.GetSourcesUseCase
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.domain.repository.ExtensionManagementRepository
import app.otakureader.domain.usecase.SearchLibraryMangaUseCase
import app.otakureader.domain.usecase.source.SearchMangaUseCase
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceManga
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    // Repository mocks — coEvery works correctly with interface mocks
    private val sourceRepository: SourceRepository = mockk()
    private val mangaRepository: MangaRepository = mockk()
    private val feedRepository: FeedRepository = mockk()
    private val generalPreferences: GeneralPreferences = mockk()
    private val extensionManagementRepository: ExtensionManagementRepository = mockk(relaxed = true)
    private val extensionRepository: ExtensionRepository = mockk()

    // Real use cases wired to repository mocks
    private val getSourcesUseCase = GetSourcesUseCase(sourceRepository)
    private val getPopularMangaUseCase = GetPopularMangaUseCase(sourceRepository)
    private val getLatestUpdatesUseCase = GetLatestUpdatesUseCase(sourceRepository)
    private val searchMangaUseCase = SearchMangaUseCase(sourceRepository)
    private val getSourceFiltersUseCase = GetSourceFiltersUseCase(sourceRepository)
    // Mocked to avoid withContext(Dispatchers.IO) racing against the test scheduler
    private val addMangaToLibraryUseCase: AddMangaToLibraryUseCase = mockk()
    private val toggleFavoriteMangaUseCase = ToggleFavoriteMangaUseCase(mangaRepository)
    private val searchLibraryMangaUseCase: SearchLibraryMangaUseCase = mockk(relaxed = true)

    private lateinit var viewModel: BrowseViewModel
    private val testDispatcher = StandardTestDispatcher()

    // Separate scope used to subscribe to state so that stateIn(WhileSubscribed) starts collecting.
    private lateinit var collectScope: CoroutineScope

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        collectScope = CoroutineScope(testDispatcher)

        // Default stubs — applied before ViewModel creation
        every { sourceRepository.getSources() } returns flowOf(emptyList())
        every { generalPreferences.showNsfwContent } returns flowOf(false)
        every { generalPreferences.enabledSourceLanguages } returns flowOf(emptySet())
        every { generalPreferences.browseSearchHistory } returns flowOf(emptyList())
        coEvery { generalPreferences.addBrowseSearchHistory(any()) } returns mockk(relaxed = true)
        coEvery { generalPreferences.getBrowseFilterState(any()) } returns null
        coEvery { generalPreferences.setBrowseFilterState(any(), any()) } just Awaits
        every { feedRepository.getSavedSearches() } returns flowOf(emptyList())
        every { generalPreferences.pinnedSourceIds } returns flowOf(emptySet())
        every { generalPreferences.sourceCategoryMap } returns flowOf(emptyMap())
        every { generalPreferences.savedSourceSearchesJson } returns flowOf("[]")
        coEvery { generalPreferences.setSavedSourceSearchesJson(any()) } just Awaits
        every { generalPreferences.lastUsedSourceIds } returns flowOf(emptyList())
        coEvery { generalPreferences.recordSourceUsed(any()) } just Awaits
        coEvery { extensionManagementRepository.refreshSources() } returns Result.success(Unit)
        every { extensionRepository.getInstalledExtensions() } returns flowOf(emptyList())

        // observeLibraryFavorites() is called in ViewModel.init and subscribes to this flow.
        every { mangaRepository.getLibraryManga() } returns flowOf(emptyList())

        // Fallback stubs for suspend calls that may be triggered by SelectSource even in search tests.
        // Individual tests can override with more specific stubs as needed.
        coEvery { sourceRepository.getPopularManga(any(), any()) } returns Result.success(MangaPage(emptyList(), false))
        coEvery { sourceRepository.getLatestUpdates(any(), any()) } returns Result.success(MangaPage(emptyList(), false))
        coEvery { sourceRepository.searchManga(any(), any(), any(), any()) } returns Result.success(MangaPage(emptyList(), false))
        coEvery { sourceRepository.getSourceFilters(any()) } returns FilterList()
        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns 1L
        coEvery { addMangaToLibraryUseCase(any<List<app.otakureader.sourceapi.SourceManga>>(), any()) } returns Result.success(1)
        coEvery { addMangaToLibraryUseCase(any<app.otakureader.sourceapi.SourceManga>(), any()) } returns Result.success(1L)

        viewModel = BrowseViewModel(
            getSourcesUseCase = getSourcesUseCase,
            getPopularMangaUseCase = getPopularMangaUseCase,
            getLatestUpdatesUseCase = getLatestUpdatesUseCase,
            searchMangaUseCase = searchMangaUseCase,
            getSourceFiltersUseCase = getSourceFiltersUseCase,
            addMangaToLibraryUseCase = addMangaToLibraryUseCase,
            toggleFavoriteMangaUseCase = toggleFavoriteMangaUseCase,
            mangaRepository = mangaRepository,
            feedRepository = feedRepository,
            generalPreferences = generalPreferences,
            searchLibraryMangaUseCase = searchLibraryMangaUseCase,
            extensionManagementRepository = extensionManagementRepository,
            extensionRepository = extensionRepository,
        )
        // Subscribe to activate stateIn(WhileSubscribed); will start collecting on first advanceUntilIdle.
        collectScope.launch { viewModel.state.collect { } }
    }

    @After
    fun teardown() {
        viewModel.viewModelScope.cancel()
        collectScope.cancel()
        Dispatchers.resetMain()
    }

    // Called after reassigning viewModel inside a test to re-activate stateIn for the new instance.
    private fun activateStateCollection() {
        collectScope.launch { viewModel.state.collect { } }
    }

    private fun createMangaSource(id: String, name: String = "Test Source", lang: String = "en", isNsfw: Boolean = false) =
        mockk<MangaSource> {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
            every { this@mockk.lang } returns lang
            every { this@mockk.baseUrl } returns ""
            every { this@mockk.isNsfw } returns isNsfw
            every { this@mockk.supportsLatest } returns true
        }

    @Test
    fun `initial state has empty sources and no loading`() = runTest {
        val state = viewModel.state.value
        assertEquals(emptyList<String>(), state.sources)
        assertEquals(false, state.isLoading)
        assertEquals(false, state.isSearching)
    }

    @Test
    fun `select source loads popular manga`() = runTest {
        val source = createMangaSource(id = "1", name = "Test Source", lang = "en", isNsfw = false)
        val mangaPage = MangaPage(
            mangas = listOf(SourceManga(title = "Manga 1", url = "url1", thumbnailUrl = null)),
            hasNextPage = true
        )

        every { sourceRepository.getSources() } returns flowOf(listOf(source))
        coEvery { sourceRepository.getPopularManga("1", 1) } returns Result.success(mangaPage)
        coEvery { sourceRepository.getSourceFilters("1") } returns FilterList()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("1", state.currentSourceId)
        assertEquals(1, state.popularManga.size)
        assertEquals("Manga 1", state.popularManga[0].title)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `search query change updates state`() = runTest {
        viewModel.onEvent(BrowseEvent.OnSearchQueryChange("One Piece"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("One Piece", viewModel.state.value.searchQuery)
    }

    @Test
    fun `search performs search with query`() = runTest {
        val source = createMangaSource(id = "1", name = "Test Source", lang = "en", isNsfw = false)
        val mangaPage = MangaPage(
            mangas = listOf(
                SourceManga(title = "One Piece", url = "url1", thumbnailUrl = null),
                SourceManga(title = "One Punch Man", url = "url2", thumbnailUrl = null)
            ),
            hasNextPage = false
        )

        every { sourceRepository.getSources() } returns flowOf(listOf(source))
        coEvery { sourceRepository.searchManga("1", "One", 1, any()) } returns Result.success(mangaPage)
        coEvery { sourceRepository.getSourceFilters("1") } returns FilterList()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(BrowseEvent.OnSearchQueryChange("One"))
        viewModel.onEvent(BrowseEvent.Search)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.searchResults.size)
        assertEquals(false, state.isSearching)
    }

    @Test
    fun `toggle filter sheet changes visibility`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.state.value.showFilterSheet)

        viewModel.onEvent(BrowseEvent.ToggleFilterSheet)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, viewModel.state.value.showFilterSheet)

        viewModel.onEvent(BrowseEvent.ToggleFilterSheet)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.state.value.showFilterSheet)
    }

    @Test
    fun `clear selection resets state`() = runTest {
        val source = createMangaSource(id = "1", name = "Test Source", lang = "en", isNsfw = false)
        val manga = SourceManga(title = "Manga 1", url = "url1", thumbnailUrl = null)

        every { sourceRepository.getSources() } returns flowOf(listOf(source))
        coEvery { sourceRepository.getPopularManga("1", 1) } returns Result.success(MangaPage(listOf(manga), false))
        coEvery { sourceRepository.getSourceFilters("1") } returns FilterList()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(BrowseEvent.OnMangaLongClick(manga))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isBulkSelectionMode)
        assertEquals(1, viewModel.state.value.selectedManga.size)

        viewModel.onEvent(BrowseEvent.ClearSelection)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isBulkSelectionMode)
        assertEquals(0, viewModel.state.value.selectedManga.size)
    }

    @Test
    fun `add selected to library sends success effect`() = runTest {
        val source = createMangaSource(id = "1", name = "Test Source", lang = "en", isNsfw = false)
        val manga = SourceManga(title = "Manga 1", url = "url1", thumbnailUrl = null)

        every { sourceRepository.getSources() } returns flowOf(listOf(source))
        coEvery { sourceRepository.getPopularManga("1", 1) } returns Result.success(MangaPage(listOf(manga), false))
        coEvery { sourceRepository.getSourceFilters("1") } returns FilterList()
        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns 1L

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(BrowseEvent.OnMangaLongClick(manga))

        // Use Turbine to collect effects; it handles the IO dispatcher resume correctly.
        // addSelectedToLibrary sends ShowSnackbar then NavigateToLibrary — consume both.
        viewModel.effect.test {
            viewModel.onEvent(BrowseEvent.AddSelectedToLibrary)
            testDispatcher.scheduler.advanceUntilIdle()

            val firstEffect = awaitItem()
            assertTrue(firstEffect is BrowseEffect.ShowSnackbar)
            awaitItem() // NavigateToLibrary — consume to avoid TurbineAssertionError
        }
    }

    @Test
    fun `load next page appends results`() = runTest {
        val source = createMangaSource(id = "1", name = "Test Source", lang = "en", isNsfw = false)
        val page1 = MangaPage(
            mangas = listOf(SourceManga(title = "Manga 1", url = "url1", thumbnailUrl = null)),
            hasNextPage = true
        )
        val page2 = MangaPage(
            mangas = listOf(SourceManga(title = "Manga 2", url = "url2", thumbnailUrl = null)),
            hasNextPage = false
        )

        every { sourceRepository.getSources() } returns flowOf(listOf(source))
        coEvery { sourceRepository.getPopularManga("1", 1) } returns Result.success(page1)
        coEvery { sourceRepository.getPopularManga("1", 2) } returns Result.success(page2)
        coEvery { sourceRepository.getSourceFilters("1") } returns FilterList()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.popularManga.size)

        viewModel.onEvent(BrowseEvent.LoadNextPage)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.popularManga.size)
        assertEquals("Manga 1", viewModel.state.value.popularManga[0].title)
        assertEquals("Manga 2", viewModel.state.value.popularManga[1].title)
    }

    @Test
    fun `refresh sources sends snackbar effect`() = runTest {
        viewModel.onEvent(BrowseEvent.RefreshSources)
        testDispatcher.scheduler.advanceUntilIdle()

        val effect = withTimeoutOrNull(1000) { viewModel.effect.first() }
        assertNotNull(effect)
        assertTrue(effect is BrowseEffect.ShowSnackbar)
    }

    @Test
    fun `search results remain visible after search completes`() = runTest {
        val source = createMangaSource(id = "1", name = "Test Source", lang = "en", isNsfw = false)
        val mangaPage = MangaPage(
            mangas = listOf(SourceManga(title = "Naruto", url = "url1", thumbnailUrl = null)),
            hasNextPage = false
        )

        every { sourceRepository.getSources() } returns flowOf(listOf(source))
        coEvery { sourceRepository.searchManga("1", "Naruto", 1, any()) } returns Result.success(mangaPage)
        coEvery { sourceRepository.getSourceFilters("1") } returns FilterList()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(BrowseEvent.OnSearchQueryChange("Naruto"))
        viewModel.onEvent(BrowseEvent.Search)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse("isSearching must be false after search completes", state.isSearching)
        assertTrue("hasSearchResults must be true after successful search", state.hasSearchResults)
        assertEquals(1, state.searchResults.size)
    }

    @Test
    fun `selecting source resets search results`() = runTest {
        val source1 = createMangaSource(id = "1", name = "Source 1", lang = "en", isNsfw = false)
        val source2 = createMangaSource(id = "2", name = "Source 2", lang = "en", isNsfw = false)
        val mangaPage = MangaPage(
            mangas = listOf(SourceManga(title = "Manga 1", url = "url1", thumbnailUrl = null)),
            hasNextPage = false
        )

        every { sourceRepository.getSources() } returns flowOf(listOf(source1, source2))
        coEvery { sourceRepository.searchManga("1", "query", 1, any()) } returns Result.success(mangaPage)
        coEvery { sourceRepository.getPopularManga("2", 1) } returns Result.success(MangaPage(emptyList(), false))
        coEvery { sourceRepository.getSourceFilters(any()) } returns FilterList()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(BrowseEvent.OnSearchQueryChange("query"))
        viewModel.onEvent(BrowseEvent.Search)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("hasSearchResults must be true after search", viewModel.state.value.hasSearchResults)

        viewModel.onEvent(BrowseEvent.SelectSource("2"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("hasSearchResults must be false after source change", viewModel.state.value.hasSearchResults)
        assertEquals(0, viewModel.state.value.searchResults.size)
    }

    @Test
    fun `clearing query resets hasSearchResults`() = runTest {
        val source = createMangaSource(id = "1", name = "Test Source", lang = "en", isNsfw = false)
        val mangaPage = MangaPage(
            mangas = listOf(SourceManga(title = "Manga", url = "url1", thumbnailUrl = null)),
            hasNextPage = false
        )

        every { sourceRepository.getSources() } returns flowOf(listOf(source))
        coEvery { sourceRepository.searchManga("1", "query", 1, any()) } returns Result.success(mangaPage)
        coEvery { sourceRepository.getSourceFilters("1") } returns FilterList()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(BrowseEvent.OnSearchQueryChange("query"))
        viewModel.onEvent(BrowseEvent.Search)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("hasSearchResults must be true after search", viewModel.state.value.hasSearchResults)

        viewModel.onEvent(BrowseEvent.OnSearchQueryChange(""))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("hasSearchResults must be false when query cleared", false, viewModel.state.value.hasSearchResults)
    }

    @Test
    fun `saved searches filtered to selected source`() = runTest {
        val search1 = FeedSavedSearch(id = 1L, sourceId = 1L, sourceName = "1", query = "one piece", filters = emptyMap())
        val search2 = FeedSavedSearch(id = 2L, sourceId = 2L, sourceName = "2", query = "naruto", filters = emptyMap())
        val source = createMangaSource(id = "1", name = "Source 1", lang = "en", isNsfw = false)

        every { feedRepository.getSavedSearches() } returns flowOf(listOf(search1, search2))
        every { sourceRepository.getSources() } returns flowOf(listOf(source))
        coEvery { sourceRepository.getSourceFilters("1") } returns FilterList()
        coEvery { sourceRepository.getPopularManga("1", 1) } returns Result.success(MangaPage(emptyList(), false))

        viewModel = BrowseViewModel(
            getSourcesUseCase = getSourcesUseCase,
            getPopularMangaUseCase = getPopularMangaUseCase,
            getLatestUpdatesUseCase = getLatestUpdatesUseCase,
            searchMangaUseCase = searchMangaUseCase,
            getSourceFiltersUseCase = getSourceFiltersUseCase,
            addMangaToLibraryUseCase = addMangaToLibraryUseCase,
            toggleFavoriteMangaUseCase = toggleFavoriteMangaUseCase,
            mangaRepository = mangaRepository,
            feedRepository = feedRepository,
            generalPreferences = generalPreferences,
            searchLibraryMangaUseCase = searchLibraryMangaUseCase,
            extensionManagementRepository = extensionManagementRepository,
            extensionRepository = extensionRepository,
        )
        activateStateCollection()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        val savedSearches = viewModel.state.value.savedSearches
        assertEquals(1, savedSearches.size)
        assertEquals("one piece", savedSearches[0].query)
    }

    @Test
    fun `saved searches update when source changes`() = runTest {
        val search1 = FeedSavedSearch(id = 1L, sourceId = 1L, sourceName = "1", query = "one piece", filters = emptyMap())
        val search2 = FeedSavedSearch(id = 2L, sourceId = 2L, sourceName = "2", query = "naruto", filters = emptyMap())
        val source1 = createMangaSource(id = "1", name = "Source 1", lang = "en", isNsfw = false)
        val source2 = createMangaSource(id = "2", name = "Source 2", lang = "en", isNsfw = false)

        val savedSearchFlow = MutableStateFlow(listOf(search1, search2))
        every { feedRepository.getSavedSearches() } returns savedSearchFlow
        every { sourceRepository.getSources() } returns flowOf(listOf(source1, source2))
        coEvery { sourceRepository.getSourceFilters(any()) } returns FilterList()
        coEvery { sourceRepository.getPopularManga(any(), 1) } returns Result.success(MangaPage(emptyList(), false))

        viewModel = BrowseViewModel(
            getSourcesUseCase = getSourcesUseCase,
            getPopularMangaUseCase = getPopularMangaUseCase,
            getLatestUpdatesUseCase = getLatestUpdatesUseCase,
            searchMangaUseCase = searchMangaUseCase,
            getSourceFiltersUseCase = getSourceFiltersUseCase,
            addMangaToLibraryUseCase = addMangaToLibraryUseCase,
            toggleFavoriteMangaUseCase = toggleFavoriteMangaUseCase,
            mangaRepository = mangaRepository,
            feedRepository = feedRepository,
            generalPreferences = generalPreferences,
            searchLibraryMangaUseCase = searchLibraryMangaUseCase,
            extensionManagementRepository = extensionManagementRepository,
            extensionRepository = extensionRepository,
        )
        activateStateCollection()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.savedSearches.size)
        assertEquals("one piece", viewModel.state.value.savedSearches[0].query)

        viewModel.onEvent(BrowseEvent.SelectSource("2"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.savedSearches.size)
        assertEquals("naruto", viewModel.state.value.savedSearches[0].query)
    }

    @Test
    fun `apply filters hides sheet and searches`() = runTest {
        val source = createMangaSource(id = "1", name = "Test Source", lang = "en", isNsfw = false)
        val mangaPage = MangaPage(
            mangas = listOf(SourceManga(title = "Manga 1", url = "url1", thumbnailUrl = null)),
            hasNextPage = false
        )

        every { sourceRepository.getSources() } returns flowOf(listOf(source))
        coEvery { sourceRepository.getSourceFilters("1") } returns FilterList()
        coEvery { sourceRepository.searchManga("1", "", 1, any()) } returns Result.success(mangaPage)

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(BrowseEvent.ToggleFilterSheet)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.showFilterSheet)

        viewModel.onEvent(BrowseEvent.ApplyFilters)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showFilterSheet)
        assertEquals(1, viewModel.state.value.searchResults.size)
    }
}
