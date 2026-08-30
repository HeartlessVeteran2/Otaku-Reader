@file:Suppress("MaxLineLength")
package app.otakureader.feature.library

import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.ContinueReadingItem
import app.otakureader.domain.model.Manga
import app.otakureader.sourceapi.toSourceId
import app.otakureader.domain.model.ReadingGoal
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.ReadingList
import app.otakureader.domain.model.ReadingListMangaItem
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.ReaderSettingsRepository
import app.otakureader.domain.repository.ReadingListRepository
import app.otakureader.domain.repository.StatisticsRepository
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.domain.usecase.GetCategoriesUseCase
import app.otakureader.domain.usecase.GetContinueReadingUseCase
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.GetRecommendationsUseCase
import app.otakureader.domain.usecase.SearchLibraryMangaUseCase
import app.otakureader.domain.usecase.ToggleFavoriteMangaUseCase
import app.otakureader.domain.usecase.downloads.ReindexDownloadsUseCase
import app.otakureader.domain.model.ReindexResult
import app.otakureader.domain.repository.EhFavoritesRepository
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.domain.repository.PageBookmarkRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.SyncEhFavoritesUseCase
import app.otakureader.domain.usecase.SyncLibraryUseCase
import app.otakureader.domain.repository.TrackerSyncRepository
import app.otakureader.domain.scheduler.LibraryUpdateScheduler
import app.cash.turbine.test
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import app.otakureader.domain.usecase.SetMangaNotificationsUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getLibraryManga: GetLibraryMangaUseCase
    private lateinit var searchLibraryManga: SearchLibraryMangaUseCase
    private lateinit var toggleFavoriteManga: ToggleFavoriteMangaUseCase
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var mangaRepository: MangaRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var settingsRepository: ReaderSettingsRepository
    private lateinit var trackRepository: TrackRepository
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private lateinit var getCategories: GetCategoriesUseCase
    private lateinit var getContinueReading: GetContinueReadingUseCase
    private lateinit var readingGoalPreferences: ReadingGoalPreferences
    private lateinit var statisticsRepository: StatisticsRepository
    private lateinit var readingListRepository: ReadingListRepository
    private lateinit var getRecommendations: GetRecommendationsUseCase
    private val libraryUpdateScheduler: LibraryUpdateScheduler = mockk(relaxed = true)
    private val reindexDownloads: ReindexDownloadsUseCase = mockk {
        coEvery { this@mockk.invoke() } returns ReindexResult(verifiedDownloads = 5, emptyDirs = 0)
    }
    private val setMangaNotifications: SetMangaNotificationsUseCase = mockk(relaxed = true)
    private val syncEhFavorites: SyncEhFavoritesUseCase = mockk(relaxed = true)
    private val ehFavoritesRepository: EhFavoritesRepository = mockk {
        every { isConfigured() } returns false
    }
    private val syncLibrary: SyncLibraryUseCase = mockk(relaxed = true)
    private val pageBookmarkRepository: PageBookmarkRepository = mockk {
        every { getMangaIdsWithBookmarks() } returns flowOf(emptySet())
    }
    private val sourceRepository: SourceRepository = mockk {
        every { getSources() } returns flowOf(emptyList())
        coEvery { getSource(any()) } returns null
    }
    private val extensionRepository: ExtensionRepository = mockk {
        every { getInstalledExtensions() } returns flowOf(emptyList())
    }

    private val sampleMangas = listOf(
        Manga(id = 1L, sourceId = 10L, url = "/m/1", title = "Naruto", favorite = true, unreadCount = 3, lastRead = 1000L, status = MangaStatus.ONGOING),
        Manga(id = 2L, sourceId = 20L, url = "/m/2", title = "Bleach", favorite = true, unreadCount = 0, lastRead = 2000L, status = MangaStatus.COMPLETED, userCompleted = true),
        Manga(id = 3L, sourceId = 10L, url = "/m/3", title = "One Piece", favorite = true, unreadCount = 7, lastRead = null, status = MangaStatus.ONGOING)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getLibraryManga = mockk()
        searchLibraryManga = mockk(relaxed = true)
        toggleFavoriteManga = mockk()
        libraryPreferences = buildLibraryPreferencesMock()
        generalPreferences = mockk {
            every { showNsfwContent } returns flowOf(true)
            every { lastUpdatesViewedAt } returns flowOf(0L)
            every { visualEffectsEnabled } returns flowOf(true)
            every { displayName } returns flowOf("")
            every { downloadedOnly } returns flowOf(false)
            coEvery { setDownloadedOnly(any()) } just Awaits
        }
        chapterRepository = mockk { every { countNewUpdatesSince(any()) } returns flowOf(0) }
        mangaRepository = mockk(relaxed = true)
        downloadRepository = mockk {
            // #1256 moved folder-name resolution onto DownloadRepository. These tests assert on
            // the numeric key they already used, because they are not about folder naming.
            coEvery { downloadFolderNameFor(any()) } answers { firstArg<Long>().toString() }
            coEvery { hasMangaDownloads(any(), any()) } returns false
            coEvery { getMangaIdsWithDownloads(any()) } returns emptySet()
            every { observeDownloads() } returns flowOf(emptyList())
        }
        settingsRepository = mockk {
            every { incognitoMode } returns flowOf(false)
        }
        trackRepository = mockk {
            every { observeEntriesForManga(any()) } returns flowOf(emptyList())
            every { observeMangaIdsWithTrackEntries() } returns flowOf(emptySet())
        }
        getCategories = mockk(relaxed = true) {
            every { this@mockk.invoke() } returns flowOf(emptyList())
            every { getMangaIdsForCategory(any()) } returns flowOf(emptyList())
        }
        getContinueReading = mockk {
            every { this@mockk.invoke() } returns flowOf(emptyList())
        }
        readingGoalPreferences = mockk {
            every { dailyChapterGoal } returns flowOf(0)
            every { weeklyChapterGoal } returns flowOf(0)
        }
        statisticsRepository = mockk {
            every { getReadingGoalProgress(any(), any()) } returns flowOf(ReadingGoal())
        }
        readingListRepository = mockk {
            every { getAllLists() } returns flowOf(emptyList())
            every { getListWithManga(any()) } returns flowOf(Pair(ReadingList(id = 0L, name = ""), emptyList()))
        }
        getRecommendations = mockk {
            every { this@mockk.invoke() } returns flowOf(emptyList())
        }
    }

    private fun buildLibraryPreferencesMock(): LibraryPreferences = mockk {
        every { gridSize } returns flowOf(3)
        every { portraitColumns } returns flowOf(0)
        every { landscapeColumns } returns flowOf(0)
        coEvery { setPortraitColumns(any()) } just Awaits
        coEvery { setLandscapeColumns(any()) } just Awaits
        every { showBadges } returns flowOf(true)
        every { showDownloadBadge } returns flowOf(true)
        every { librarySortMode } returns flowOf(0)
        every { libraryFilterMode } returns flowOf(0)
        every { libraryFilterSourceId } returns flowOf(null)
        every { isStaggeredGrid } returns flowOf(false)
        every { libraryDisplayMode } returns flowOf(0)
        every { showRecommendations } returns flowOf(false)
        every { dismissedRecommendations } returns flowOf(emptySet())
        every { groupType } returns flowOf(LibraryGroup.BY_DEFAULT)
        every { savedViewsJson } returns flowOf("[]")
        coEvery { setSavedViewsJson(any()) } just Awaits
        every { showTitle } returns flowOf(true)
        every { showCategoryTabs } returns flowOf(true)
        every { showCategoryItemCount } returns flowOf(true)
        every { showContinueReadingButton } returns flowOf(true)
        coEvery { setShowCategoryTabs(any()) } just Awaits
        coEvery { setShowCategoryItemCount(any()) } just Awaits
        coEvery { setShowContinueReadingButton(any()) } just Awaits
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(
            getLibraryManga,
            searchLibraryManga,
            toggleFavoriteManga,
            libraryPreferences,
            generalPreferences,
            chapterRepository,
            mangaRepository,
            downloadRepository,
            settingsRepository,
            trackRepository,
            categoryRepository,
            getCategories,
            getContinueReading,
            readingGoalPreferences,
            statisticsRepository,
            readingListRepository,
            getRecommendations,
            libraryUpdateScheduler,
            reindexDownloads,
            setMangaNotifications,
            syncEhFavorites,
            ehFavoritesRepository,
            syncLibrary,
            pageBookmarkRepository,
            sourceRepository,
            extensionRepository,
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
    fun loadLibrary_populatesSourceIconUrl_fromInstalledExtensions() = runTest {
        // A manga row stores the *canonical* key — the extension's source id stringified and
        // hashed, matching what `TachiyomiSourceAdapter` exposes. Keying the icon map by the raw
        // `ExtensionSource.id` instead meant no row ever matched and no icons were shown.
        val installedKey = INSTALLED_SOURCE_ID.toString().toSourceId()
        val uninstalledKey = "99".toSourceId()
        every { getLibraryManga() } returns flowOf(
            sampleMangas.mapIndexed { index, manga ->
                manga.copy(sourceId = if (index == 1) uninstalledKey else installedKey)
            }
        )
        val testIconUrl = "https://test.icon/source10.png"
        val fakeExtension = app.otakureader.core.extension.domain.model.Extension(
            id = 1L,
            pkgName = "eu.kanade.tachiyomi.extension.en.testsource",
            name = "Test Source",
            versionCode = 1,
            versionName = "1.0",
            sources = listOf(
                app.otakureader.core.extension.domain.model.ExtensionSource(
                    id = INSTALLED_SOURCE_ID,
                    name = "Test Source",
                    lang = "en",
                    baseUrl = "https://example.com",
                )
            ),
            status = app.otakureader.core.extension.domain.model.InstallStatus.INSTALLED,
            apkPath = null,
            iconUrl = testIconUrl,
            lang = "en",
            isNsfw = false,
            installDate = null,
            signatureHash = "abc123",
        )
        every { extensionRepository.getInstalledExtensions() } returns flowOf(listOf(fakeExtension))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val matched = viewModel.state.value.mangaList.filter { it.sourceId == installedKey }
        val unmatched = viewModel.state.value.mangaList.filter { it.sourceId == uninstalledKey }
        // Both non-empty, or "every entry has the right icon" would hold vacuously.
        assertTrue(matched.isNotEmpty())
        assertTrue(unmatched.isNotEmpty())
        matched.forEach { assertEquals(testIconUrl, it.sourceIconUrl) }
        unmatched.forEach { assertNull(it.sourceIconUrl) }
    }

    @Test
    fun loadLibrary_marksTrackedAndDownloadedMangaFromBatchedLookups() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)
        every { trackRepository.observeMangaIdsWithTrackEntries() } returns flowOf(setOf(2L))
        coEvery { downloadRepository.getMangaIdsWithDownloads(any()) } returns setOf(1L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val byId = viewModel.state.value.mangaList.associateBy { it.id }
        assertTrue(byId.getValue(1L).isDownloaded)
        assertFalse(byId.getValue(1L).hasTracking)
        assertFalse(byId.getValue(2L).isDownloaded)
        assertTrue(byId.getValue(2L).hasTracking)
        assertFalse(byId.getValue(3L).isDownloaded)
        assertFalse(byId.getValue(3L).hasTracking)
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
        testDispatcher.scheduler.advanceUntilIdle()

        // Long-press directly toggles selection (Komikku parity — no context menu indirection).
        assertTrue(viewModel.state.value.selectedManga.contains(1L))
    }

    @Test
    fun onEvent_OnMangaLongClick_twice_selectsBoth() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.OnMangaLongClick(1L))
        viewModel.onEvent(LibraryEvent.OnMangaLongClick(2L))
        testDispatcher.scheduler.advanceUntilIdle()

        // Each long-press toggles its own manga into selection.
        assertEquals(setOf(1L, 2L), viewModel.state.value.selectedManga)
    }

    @Test
    fun onEvent_ClearSelection_removesAllSelections() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.SelectMangaFromMenu(1L))
        viewModel.onEvent(LibraryEvent.SelectMangaFromMenu(2L))
        viewModel.onEvent(LibraryEvent.ClearSelection)

        assertTrue(viewModel.state.value.selectedManga.isEmpty())
    }

    @Test
    fun onEvent_SelectAllManga_selectsEveryDisplayedManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.SelectAllManga)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            sampleMangas.map { it.id }.toSet(),
            viewModel.state.value.selectedManga
        )
    }

    @Test
    fun onEvent_MarkSelectedAsRead_batchesChapterLookupAcrossSelectedManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)
        val chapters = listOf(
            Chapter(id = 100L, mangaId = 1L, url = "/c/100", name = "Ch. 1"),
            Chapter(id = 200L, mangaId = 2L, url = "/c/200", name = "Ch. 1"),
        )
        coEvery { chapterRepository.getChaptersByMangaIdsSync(setOf(1L, 2L)) } returns chapters
        coEvery { chapterRepository.updateChapterProgress(any<Collection<Long>>(), any(), any()) } just Awaits

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.OnMangaLongClick(1L))
        viewModel.onEvent(LibraryEvent.OnMangaLongClick(2L))
        viewModel.onEvent(LibraryEvent.MarkSelectedAsRead)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chapterRepository.getChaptersByMangaIdsSync(setOf(1L, 2L)) }
        coVerify { chapterRepository.updateChapterProgress(listOf(100L, 200L), read = true, lastPageRead = 0) }
    }

    @Test
    fun onEvent_InvertSelection_togglesSelectionAcrossDisplayedManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Select via the context-menu "Select" action (long-press now opens a menu, not direct selection).
        viewModel.onEvent(LibraryEvent.SelectMangaFromMenu(1L))
        viewModel.onEvent(LibraryEvent.InvertSelection)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1 was selected, so after invert only 2 and 3 should remain selected.
        assertEquals(setOf(2L, 3L), viewModel.state.value.selectedManga)
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

        // Enter selection mode via the context-menu "Select" action (long-press now opens menu first).
        viewModel.onEvent(LibraryEvent.SelectMangaFromMenu(2L))
        testDispatcher.scheduler.advanceUntilIdle()
        // Then click another item — should add to selection
        viewModel.onEvent(LibraryEvent.OnMangaClick(1L))
        testDispatcher.scheduler.advanceUntilIdle()

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
            throw IllegalStateException("Database error")
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

    // --- Tristate filter tests (Komikku parity) ---

    @Test
    fun triStateFilter_unread_ENABLED_IS_showsOnlyUnreadManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        viewModel.onEvent(LibraryEvent.SetFilterUnread(LibraryTriState.ENABLED_IS))
        testDispatcher.scheduler.advanceUntilIdle()

        // Only Naruto(unread=3) and One Piece(unread=7) have unread > 0
        assertEquals(2, viewModel.state.value.mangaList.size)
        assertTrue(viewModel.state.value.mangaList.none { it.id == 2L })
    }

    @Test
    fun triStateFilter_unread_ENABLED_NOT_excludesUnreadManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        viewModel.onEvent(LibraryEvent.SetFilterUnread(LibraryTriState.ENABLED_NOT))
        testDispatcher.scheduler.advanceUntilIdle()

        // Only Bleach has unreadCount = 0
        assertEquals(1, viewModel.state.value.mangaList.size)
        assertEquals(2L, viewModel.state.value.mangaList.first().id)
    }

    @Test
    fun triStateFilter_completed_ENABLED_IS_showsOnlyCompletedManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        viewModel.onEvent(LibraryEvent.SetFilterCompleted(LibraryTriState.ENABLED_IS))
        testDispatcher.scheduler.advanceUntilIdle()

        // Only Bleach has userCompleted = true
        assertEquals(1, viewModel.state.value.mangaList.size)
        assertEquals(2L, viewModel.state.value.mangaList.first().id)
    }

    @Test
    fun triStateFilter_DISABLED_showsAllManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // All filters DISABLED by default — all 3 items shown
        assertEquals(3, viewModel.state.value.mangaList.size)
    }

    @Test
    fun triStateFilter_clearAll_resetsAllTristates() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        viewModel.onEvent(LibraryEvent.SetFilterUnread(LibraryTriState.ENABLED_IS))
        viewModel.onEvent(LibraryEvent.SetFilterCompleted(LibraryTriState.ENABLED_NOT))
        viewModel.onEvent(LibraryEvent.ClearAllFilters)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryTriState.DISABLED, viewModel.state.value.filterUnread)
        assertEquals(LibraryTriState.DISABLED, viewModel.state.value.filterCompleted)
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

    // --- Reading goal tests ---

    @Test
    fun observeGoalProgress_updatesStateWithGoal() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())
        val testGoal = ReadingGoal(dailyGoal = 5, dailyProgress = 3, weeklyGoal = 20, weeklyProgress = 10)
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingGoalPreferences.weeklyChapterGoal } returns flowOf(20)
        every { statisticsRepository.getReadingGoalProgress(5, 20) } returns flowOf(testGoal)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testGoal, viewModel.state.value.readingGoal)
    }

    @Test
    fun observeGoalProgress_reactsToGoalChanges() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())
        val initialGoal = ReadingGoal(dailyGoal = 5, dailyProgress = 2)
        val updatedGoal = ReadingGoal(dailyGoal = 10, dailyProgress = 2)

        val dailyGoalFlow = MutableStateFlow(5)
        every { readingGoalPreferences.dailyChapterGoal } returns dailyGoalFlow
        every { readingGoalPreferences.weeklyChapterGoal } returns flowOf(0)
        every { statisticsRepository.getReadingGoalProgress(5, 0) } returns flowOf(initialGoal)
        every { statisticsRepository.getReadingGoalProgress(10, 0) } returns flowOf(updatedGoal)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(initialGoal, viewModel.state.value.readingGoal)

        // Change goal
        dailyGoalFlow.value = 10
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(updatedGoal, viewModel.state.value.readingGoal)
    }

    @Test
    fun setFilterReadingList_withListId_setsReadingListMode() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())
        val list = ReadingList(id = 42L, name = "Favorites", itemCount = 3)
        every { readingListRepository.getAllLists() } returns flowOf(listOf(list))
        every { readingListRepository.getListWithManga(42L) } returns flowOf(Pair(list, emptyList()))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.SetFilterReadingList(42L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(42L, viewModel.state.value.filterReadingListId)
    }

    @Test
    fun setFilterReadingList_withNullId_resetsToAll() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())
        val list = ReadingList(id = 42L, name = "Favorites", itemCount = 3)
        every { readingListRepository.getAllLists() } returns flowOf(listOf(list))
        every { readingListRepository.getListWithManga(42L) } returns flowOf(Pair(list, emptyList()))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.SetFilterReadingList(42L))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(42L, viewModel.state.value.filterReadingListId)

        viewModel.onEvent(LibraryEvent.SetFilterReadingList(null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.filterReadingListId)
    }

    @Test
    fun readingListFilter_filtersToListManga() = runTest {
        val mangaInList = sampleMangas[0]
        val mangaNotInList = sampleMangas[1]
        every { getLibraryManga() } returns flowOf(listOf(mangaInList, mangaNotInList))

        val list = ReadingList(id = 1L, name = "My List", itemCount = 1)
        val listMangaItem = ReadingListMangaItem(manga = mangaInList)
        every { readingListRepository.getAllLists() } returns flowOf(listOf(list))
        every { readingListRepository.getListWithManga(1L) } returns flowOf(Pair(list, listOf(listMangaItem)))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.SetFilterReadingList(1L))
        testDispatcher.scheduler.advanceUntilIdle()

        val mangaList = viewModel.state.value.mangaList
        assertEquals(1, mangaList.size)
        assertEquals(mangaInList.id, mangaList[0].id)
    }

    // --- #864 Download Badge tests ---

    @Test
    fun downloadCountByManga_derivedCorrectly_fromObserveDownloads() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())
        val downloads = listOf(
            DownloadItem(id = 1L, mangaId = 10L, chapterId = 100L, mangaTitle = "Naruto", chapterTitle = "Ch 1"),
            DownloadItem(id = 2L, mangaId = 10L, chapterId = 101L, mangaTitle = "Naruto", chapterTitle = "Ch 2"),
            DownloadItem(id = 3L, mangaId = 20L, chapterId = 200L, mangaTitle = "Bleach", chapterTitle = "Ch 1"),
        )
        every { downloadRepository.observeDownloads() } returns flowOf(downloads)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val countMap = viewModel.state.value.downloadCountByManga
        assertEquals(2, countMap[10L])
        assertEquals(1, countMap[20L])
        assertNull(countMap[99L])
    }

    // --- #867 Incognito Mode tests ---

    @Test
    fun toggleIncognito_callsSetIncognitoMode_withNegatedCurrent() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())
        val incognitoFlow = MutableStateFlow(false)
        every { settingsRepository.incognitoMode } returns incognitoFlow
        coEvery { settingsRepository.setIncognitoMode(any()) } answers {
            incognitoFlow.value = firstArg()
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.incognitoMode)

        viewModel.onEvent(LibraryEvent.ToggleIncognito)
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify { settingsRepository.setIncognitoMode(true) }
        assertTrue(viewModel.state.value.incognitoMode)
    }

    @Test
    fun incognitoMode_stateReflectsRepositoryValue() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())
        every { settingsRepository.incognitoMode } returns flowOf(true)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.incognitoMode)
    }

    @Test
    fun reindexDownloads_callsUseCaseAndEmitsSnackbar() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LibraryEvent.ReindexDownloads)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is LibraryEffect.ShowSnackbar)
            val snackbar = effect as LibraryEffect.ShowSnackbar
            assertEquals(R.string.library_reindex_complete, snackbar.messageRes)
            assertEquals(listOf(5), snackbar.formatArgs)
        }
    }

    @Test
    fun migrateSelected_emitsNavigateToMigrationWithSelectedIds_andClearsSelection() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.SelectMangaFromMenu(1L))
        viewModel.onEvent(LibraryEvent.SelectMangaFromMenu(2L))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LibraryEvent.MigrateSelected)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is LibraryEffect.NavigateToMigration)
            assertEquals(
                listOf(1L, 2L),
                (effect as LibraryEffect.NavigateToMigration).selectedMangaIds.sorted()
            )
        }

        assertTrue(viewModel.state.value.selectedManga.isEmpty())
    }

    @Test
    fun continueReadingClick_prefersHistoryChapter_whenContinueReadingItemPresent() = runTest {
        val mangaId = 42L
        val historyChapterId = 100L
        val continueReadingItem = ContinueReadingItem(
            mangaId = mangaId,
            chapterId = historyChapterId,
            mangaTitle = "Test Manga",
            thumbnailUrl = null,
            chapterName = "Chapter 5",
            chapterNumber = 5f,
            lastPageRead = 3,
            readAt = System.currentTimeMillis(),
        )
        every { getLibraryManga() } returns flowOf(emptyList())
        every { getContinueReading() } returns flowOf(listOf(continueReadingItem))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LibraryEvent.ContinueReadingClick(mangaId))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is LibraryEffect.NavigateToReader)
            val nav = effect as LibraryEffect.NavigateToReader
            assertEquals(mangaId, nav.mangaId)
            assertEquals(historyChapterId, nav.chapterId)
        }
    }

    @Test
    fun continueReadingClick_fallsBackToNextUnread_whenNoHistoryEntry() = runTest {
        val mangaId = 42L
        val nextUnreadChapterId = 200L
        every { getLibraryManga() } returns flowOf(emptyList())
        every { getContinueReading() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns
            Chapter(id = nextUnreadChapterId, mangaId = mangaId, url = "", name = "Chapter 1")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LibraryEvent.ContinueReadingClick(mangaId))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is LibraryEffect.NavigateToReader)
            val nav = effect as LibraryEffect.NavigateToReader
            assertEquals(mangaId, nav.mangaId)
            assertEquals(nextUnreadChapterId, nav.chapterId)
        }
    }

    @Test
    fun continueReadingClick_sendsNoEffect_whenNeitherHistoryNorNextUnreadExists() = runTest {
        val mangaId = 42L
        every { getLibraryManga() } returns flowOf(emptyList())
        every { getContinueReading() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns null

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LibraryEvent.ContinueReadingClick(mangaId))
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }
    }

    // --- UpdateSelected tests ---

    @Test
    fun updateSelected_triggersSchedulerAndEmitsSnackbar_andClearsSelection() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LibraryEvent.SelectMangaFromMenu(1L))
        viewModel.onEvent(LibraryEvent.SelectMangaFromMenu(2L))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LibraryEvent.UpdateSelected)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is LibraryEffect.ShowSnackbar)
            assertEquals(R.string.library_update_started, (effect as LibraryEffect.ShowSnackbar).messageRes)
        }

        io.mockk.coVerify(exactly = 1) { libraryUpdateScheduler.enqueueNow() }
        assertTrue(viewModel.state.value.selectedManga.isEmpty())
    }

    @Test
    fun updateSelected_doesNothing_whenSelectionIsEmpty() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LibraryEvent.UpdateSelected)
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }

        io.mockk.coVerify(exactly = 0) { libraryUpdateScheduler.enqueueNow() }
    }

    // --- New sort mode tests (CHAPTER_FETCH_DATE, TRACKER_MEAN) ---

    @Test
    fun sortMode_CHAPTER_FETCH_DATE_sortsByDateAddedDescending() = runTest {
        every { libraryPreferences.librarySortMode } returns flowOf(LibrarySortMode.CHAPTER_FETCH_DATE.ordinal)
        val mangasWithDates = listOf(
            Manga(id = 1L, sourceId = 10L, url = "/m/1", title = "Naruto", favorite = true, dateAdded = 3000L),
            Manga(id = 2L, sourceId = 20L, url = "/m/2", title = "Bleach", favorite = true, dateAdded = 1000L),
            Manga(id = 3L, sourceId = 10L, url = "/m/3", title = "One Piece", favorite = true, dateAdded = 2000L),
        )
        every { getLibraryManga() } returns flowOf(mangasWithDates)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Naruto(3000) > One Piece(2000) > Bleach(1000)
        val ids = viewModel.state.value.mangaList.map { it.id }
        assertEquals(listOf(1L, 3L, 2L), ids)
    }

    @Test
    fun sortMode_TRACKER_MEAN_sortsByTitleAscending() = runTest {
        every { libraryPreferences.librarySortMode } returns flowOf(LibrarySortMode.TRACKER_MEAN.ordinal)
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Falls back to alphabetical: Bleach, Naruto, One Piece
        val titles = viewModel.state.value.mangaList.map { it.title }
        assertEquals(listOf("Bleach", "Naruto", "One Piece"), titles)
    }

    private companion object {
        /** Matches the raw Tachiyomi id an installed extension reports. */
        const val INSTALLED_SOURCE_ID = 10L
    }

    // ── Per-series new-chapter notifications (#1131) ─────────────────────────

    private fun selectAllThree(viewModel: LibraryViewModel) {
        listOf(1L, 2L, 3L).forEach { viewModel.onEvent(LibraryEvent.OnMangaLongClick(it)) }
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /**
     * Mixed selection turns everything **on**. Anything else would leave the user tapping twice to
     * reach a state they can predict, and would silently mute titles they had already enabled.
     */
    @Test
    fun toggleSelectedNotifications_enablesAllWhenAnyAreOff() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)
        coEvery { mangaRepository.getMangaByIds(any()) } returns listOf(
            sampleMangas[0].copy(notifyNewChapters = true),
            sampleMangas[1].copy(notifyNewChapters = false),
            sampleMangas[2].copy(notifyNewChapters = true),
        )
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectAllThree(viewModel)

        viewModel.onEvent(LibraryEvent.ToggleSelectedNotifications)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 3) { setMangaNotifications(any(), true) }
        coVerify(exactly = 0) { setMangaNotifications(any(), false) }
    }

    /** Only when every selected title is already on does the button mean "turn them off". */
    @Test
    fun toggleSelectedNotifications_disablesAllWhenEveryOneIsOn() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)
        coEvery { mangaRepository.getMangaByIds(any()) } returns
            sampleMangas.map { it.copy(notifyNewChapters = true) }
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectAllThree(viewModel)

        viewModel.onEvent(LibraryEvent.ToggleSelectedNotifications)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 3) { setMangaNotifications(any(), false) }
    }

    /**
     * An empty lookup must not read as "everything is already on".
     *
     * `all {}` is vacuously true on an empty list, so a failed or racing read would otherwise mute
     * every selected title — the destructive direction, chosen by an absence of data.
     */
    @Test
    fun toggleSelectedNotifications_treatsAnEmptyLookupAsEnable() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)
        coEvery { mangaRepository.getMangaByIds(any()) } returns emptyList()
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectAllThree(viewModel)

        viewModel.onEvent(LibraryEvent.ToggleSelectedNotifications)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { setMangaNotifications(any(), false) }
    }

    /** The selection clears, like every other bulk action. */
    @Test
    fun toggleSelectedNotifications_clearsTheSelection() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)
        coEvery { mangaRepository.getMangaByIds(any()) } returns sampleMangas
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectAllThree(viewModel)

        viewModel.onEvent(LibraryEvent.ToggleSelectedNotifications)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.selectedManga.isEmpty())
    }
}
