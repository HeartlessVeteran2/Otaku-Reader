package app.otakureader.feature.browse

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.model.SavedSourceSearch
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
import app.otakureader.sourceapi.toSourceId
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        // #1258: BrowseViewModel now also observes whether the initial load is still running, so
        // it can tell "no sources installed" from "not loaded yet". These tests all describe the
        // settled state, so loading is false throughout.
        every { sourceRepository.isLoadingSources() } returns flowOf(false)
        every { generalPreferences.showNsfwContent } returns flowOf(false)
        every { generalPreferences.enabledSourceLanguages } returns flowOf(emptySet())
        every { generalPreferences.browseSearchHistory } returns flowOf(emptyList())
        coEvery { generalPreferences.addBrowseSearchHistory(any()) } returns mockk(relaxed = true)
        coEvery { generalPreferences.getBrowseFilterState(any()) } returns null
        coEvery { generalPreferences.setBrowseFilterState(any(), any()) } just Awaits
        every { generalPreferences.pinnedSourceIds } returns flowOf(emptySet())
        every { generalPreferences.disabledSourceIds } returns flowOf(emptySet())
        coEvery { generalPreferences.toggleDisabledSource(any()) } just Awaits
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

    /** Awaits state emissions until [predicate] matches, bounded so a wrong stub can't hang the test. */
    private suspend fun ReceiveTurbine<BrowseState>.awaitUntil(
        predicate: (BrowseState) -> Boolean,
    ): BrowseState {
        var state = awaitItem()
        var attempts = 0
        while (!predicate(state) && attempts < MAX_AWAIT_ATTEMPTS) {
            state = awaitItem()
            attempts++
        }
        return state
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
    fun `MoveNamedSavedSearchDown swaps order with the next search for the same source`() = runTest {
        val searchA = SavedSourceSearch(id = "a", name = "A", query = "a", sourceId = 1L, sourceName = "1", order = 0)
        val searchB = SavedSourceSearch(id = "b", name = "B", query = "b", sourceId = 1L, sourceName = "1", order = 1)
        val otherSourceSearch = SavedSourceSearch(id = "c", name = "C", query = "c", sourceId = 2L, sourceName = "2", order = 0)
        val initialJson = Json.encodeToString(listOf(searchA, searchB, otherSourceSearch))

        every { generalPreferences.savedSourceSearchesJson } returns flowOf(initialJson)
        val writtenJson = slot<String>()
        coEvery { generalPreferences.setSavedSourceSearchesJson(capture(writtenJson)) } just Awaits

        viewModel = BrowseViewModel(
            getSourcesUseCase = getSourcesUseCase,
            getPopularMangaUseCase = getPopularMangaUseCase,
            getLatestUpdatesUseCase = getLatestUpdatesUseCase,
            searchMangaUseCase = searchMangaUseCase,
            getSourceFiltersUseCase = getSourceFiltersUseCase,
            addMangaToLibraryUseCase = addMangaToLibraryUseCase,
            toggleFavoriteMangaUseCase = toggleFavoriteMangaUseCase,
            mangaRepository = mangaRepository,
            generalPreferences = generalPreferences,
            searchLibraryMangaUseCase = searchLibraryMangaUseCase,
            extensionManagementRepository = extensionManagementRepository,
            extensionRepository = extensionRepository,
        )
        activateStateCollection()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(BrowseEvent.MoveNamedSavedSearchDown("a"))
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = Json.decodeFromString<List<SavedSourceSearch>>(writtenJson.captured).associateBy { it.id }
        assertEquals(1, updated.getValue("a").order)
        assertEquals(0, updated.getValue("b").order)
        // Other sources' searches are untouched — this is the fix for the data-loss bug where
        // persisting only the currently-filtered subset would have dropped search "c" entirely.
        assertEquals(0, updated.getValue("c").order)
    }

    @Test
    fun `MoveNamedSavedSearchDown still reorders when neighbors share the same legacy order value`() = runTest {
        // Simulates entries persisted before the `order` field existed — they all decode to the
        // default 0. Without normalizing first, swapping two same-valued neighbors would write
        // the same value back to both and silently do nothing.
        val searchA = SavedSourceSearch(id = "a", name = "A", query = "a", sourceId = 1L, sourceName = "1", order = 0)
        val searchB = SavedSourceSearch(id = "b", name = "B", query = "b", sourceId = 1L, sourceName = "1", order = 0)
        val initialJson = Json.encodeToString(listOf(searchA, searchB))

        every { generalPreferences.savedSourceSearchesJson } returns flowOf(initialJson)
        val writtenJson = slot<String>()
        coEvery { generalPreferences.setSavedSourceSearchesJson(capture(writtenJson)) } just Awaits

        viewModel = BrowseViewModel(
            getSourcesUseCase = getSourcesUseCase,
            getPopularMangaUseCase = getPopularMangaUseCase,
            getLatestUpdatesUseCase = getLatestUpdatesUseCase,
            searchMangaUseCase = searchMangaUseCase,
            getSourceFiltersUseCase = getSourceFiltersUseCase,
            addMangaToLibraryUseCase = addMangaToLibraryUseCase,
            toggleFavoriteMangaUseCase = toggleFavoriteMangaUseCase,
            mangaRepository = mangaRepository,
            generalPreferences = generalPreferences,
            searchLibraryMangaUseCase = searchLibraryMangaUseCase,
            extensionManagementRepository = extensionManagementRepository,
            extensionRepository = extensionRepository,
        )
        activateStateCollection()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(BrowseEvent.MoveNamedSavedSearchDown("a"))
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = Json.decodeFromString<List<SavedSourceSearch>>(writtenJson.captured).associateBy { it.id }
        assertEquals(1, updated.getValue("a").order)
        assertEquals(0, updated.getValue("b").order)
    }

    @Test
    fun `MoveNamedSavedSearchUp on the first item is a no-op`() = runTest {
        val searchA = SavedSourceSearch(id = "a", name = "A", query = "a", sourceId = 1L, sourceName = "1", order = 0)
        val searchB = SavedSourceSearch(id = "b", name = "B", query = "b", sourceId = 1L, sourceName = "1", order = 1)
        val initialJson = Json.encodeToString(listOf(searchA, searchB))

        every { generalPreferences.savedSourceSearchesJson } returns flowOf(initialJson)

        viewModel = BrowseViewModel(
            getSourcesUseCase = getSourcesUseCase,
            getPopularMangaUseCase = getPopularMangaUseCase,
            getLatestUpdatesUseCase = getLatestUpdatesUseCase,
            searchMangaUseCase = searchMangaUseCase,
            getSourceFiltersUseCase = getSourceFiltersUseCase,
            addMangaToLibraryUseCase = addMangaToLibraryUseCase,
            toggleFavoriteMangaUseCase = toggleFavoriteMangaUseCase,
            mangaRepository = mangaRepository,
            generalPreferences = generalPreferences,
            searchLibraryMangaUseCase = searchLibraryMangaUseCase,
            extensionManagementRepository = extensionManagementRepository,
            extensionRepository = extensionRepository,
        )
        activateStateCollection()

        viewModel.onEvent(BrowseEvent.SelectSource("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(BrowseEvent.MoveNamedSavedSearchUp("a"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { generalPreferences.setSavedSourceSearchesJson(any()) }
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

    @Test
    fun `buildSourceFilterResult excludes disabled sources from the filtered list`() {
        val visible = createMangaSource(id = "1", name = "Visible Source", lang = "en", isNsfw = false)
        val disabled = createMangaSource(id = "2", name = "Disabled Source", lang = "en", isNsfw = false)
        // Real disabledSourceIds are always source.id.toSourceId() (a hashCode-derived Long,
        // not the literal numeric string) — match that here rather than hardcoding a Long.
        val disabledId = disabled.id.toSourceId()

        val result = buildSourceFilterResult(
            sources = listOf(visible, disabled),
            showNsfw = false,
            enabledLangs = emptySet(),
            disabledIds = setOf(disabledId),
        )

        assertEquals(listOf("1"), result.sources.map { it.id })
        assertEquals(setOf(disabledId), result.disabledIds)
    }

    @Test
    fun `buildSourceFilterResult is a no-op when nothing is disabled`() {
        val source1 = createMangaSource(id = "1", name = "Source 1", lang = "en", isNsfw = false)
        val source2 = createMangaSource(id = "2", name = "Source 2", lang = "en", isNsfw = false)

        val result = buildSourceFilterResult(
            sources = listOf(source1, source2),
            showNsfw = false,
            enabledLangs = emptySet(),
            disabledIds = emptySet(),
        )

        assertEquals(setOf("1", "2"), result.sources.map { it.id }.toSet())
    }

    @Test
    fun `ToggleDisableSource calls generalPreferences toggleDisabledSource`() = runTest {
        viewModel.onEvent(BrowseEvent.ToggleDisableSource(5L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { generalPreferences.toggleDisabledSource(5L) }
    }

    @Test
    fun `ShowSourcesFilterDialog and DismissSourcesFilterDialog toggle dialog visibility`() = runTest {
        viewModel.state.test {
            val initial = awaitItem()
            assertFalse(initial.showSourcesFilterDialog)

            viewModel.onEvent(BrowseEvent.ShowSourcesFilterDialog)
            val shown = awaitUntil { it.showSourcesFilterDialog }
            assertTrue(shown.showSourcesFilterDialog)

            viewModel.onEvent(BrowseEvent.DismissSourcesFilterDialog)
            val dismissed = awaitUntil { !it.showSourcesFilterDialog }
            assertFalse(dismissed.showSourcesFilterDialog)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val MAX_AWAIT_ATTEMPTS = 20
    }
}
