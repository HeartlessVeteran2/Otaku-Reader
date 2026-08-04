package app.otakureader.feature.details

import androidx.lifecycle.SavedStateHandle
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.model.Category
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadStatus
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaMetadata
import app.otakureader.domain.model.MangaMetadataTag
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaMetadataRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.ReadingListRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.domain.tracking.Tracker
import app.otakureader.domain.usecase.SetMangaNotificationsUseCase
import app.otakureader.domain.usecase.UpdateMangaNoteUseCase
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// DetailsViewModel covers a large surface area (favorite/categories, chapters, downloads,
// reader settings, tracking, notes, custom cover...), so its test class grows past Detekt's
// LargeClass threshold along with it. Splitting by concern would fragment the shared fixtures
// and mock setup more than it would help readability.
@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mangaId = 42L

    private lateinit var mangaRepository: MangaRepository
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var sourceRepository: SourceRepository
    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var updateMangaNote: UpdateMangaNoteUseCase
    private lateinit var setMangaNotifications: SetMangaNotificationsUseCase
    private lateinit var statisticsRepository: app.otakureader.domain.repository.StatisticsRepository
    private lateinit var trackRepository: TrackRepository
    private lateinit var readingListRepository: ReadingListRepository
    private lateinit var metadataRepository: MangaMetadataRepository
    private lateinit var anilistTracker: Tracker
    private lateinit var savedStateHandle: SavedStateHandle

    private val sampleManga = Manga(
        id = mangaId,
        sourceId = 1L,
        url = "/m/42",
        title = "Attack on Titan",
        status = MangaStatus.COMPLETED,
        favorite = false,
        notifyNewChapters = true
    )

    private val sampleChapters = listOf(
        Chapter(id = 1L, mangaId = mangaId, url = "/c/1", name = "Chapter 1", chapterNumber = 1.0f, read = true),
        Chapter(id = 2L, mangaId = mangaId, url = "/c/2", name = "Chapter 2", chapterNumber = 2.0f, read = false),
        Chapter(id = 3L, mangaId = mangaId, url = "/c/3", name = "Chapter 3", chapterNumber = 3.0f, read = false)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mangaRepository = mockk()
        chapterRepository = mockk()
        categoryRepository = mockk(relaxed = true)
        downloadRepository = mockk()
        sourceRepository = mockk(relaxed = true)
        downloadPreferences = mockk()
        generalPreferences = mockk(relaxed = true)
        updateMangaNote = mockk()
        setMangaNotifications = mockk()
        statisticsRepository = mockk(relaxed = true)
        trackRepository = mockk(relaxed = true)
        readingListRepository = mockk(relaxed = true)
        metadataRepository = mockk(relaxed = true)
        every { metadataRepository.observeMetadata(mangaId) } returns flowOf(null)
        anilistTracker = mockk(relaxed = true)
        every { anilistTracker.id } returns TrackerType.ANILIST
        every { anilistTracker.name } returns "AniList"
        savedStateHandle = SavedStateHandle(mapOf(DetailsViewModel.MANGA_ID_ARG to mangaId))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DetailsViewModel {
        return DetailsViewModel(
            savedStateHandle,
            mangaRepository,
            chapterRepository,
            categoryRepository,
            downloadRepository,
            sourceRepository,
            downloadPreferences,
            generalPreferences,
            updateMangaNote,
            setMangaNotifications,
            statisticsRepository,
            trackRepository,
            readingListRepository,
            metadataRepository,
            trackers = setOf(anilistTracker),
        )
    }

    private fun stubManga(manga: Manga?) {
        every { mangaRepository.getMangaByIdFlow(mangaId) } returns flowOf(manga)
        coEvery { mangaRepository.getMangaById(mangaId) } returns manga
    }

    private fun setUpDefaultMocks() {
        stubManga(sampleManga)
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(sampleChapters)
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(false)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns sampleChapters[1]
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
        coEvery { mangaRepository.updateChapterFlags(any(), any()) } returns Unit
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        // Result is a value class, which a relaxed mock cannot fabricate — stub it explicitly.
        coEvery { metadataRepository.refreshMetadata(any(), any(), any()) } returns
            Result.success(sampleMetadata)
    }

    // ---- Initial load ----

    @Test
    fun init_loadsMangaAndChapters() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(sampleManga, state.manga)
        assertEquals(3, state.chapters.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun init_loadsSourceNameFromSourceRepository() = runTest {
        setUpDefaultMocks()
        val source = mockk<app.otakureader.sourceapi.MangaSource>(relaxed = true)
        every { source.name } returns "MangaDex"
        coEvery { sourceRepository.getSource(sampleManga.sourceId.toString()) } returns source

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("MangaDex", viewModel.state.value.sourceName)
    }

    @Test
    fun init_setsFavoriteStatus() = runTest {
        setUpDefaultMocks()
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(true)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isFavorite)
    }

    @Test
    fun init_loadsNextUnreadChapter() = runTest {
        setUpDefaultMocks()
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns sampleChapters[1]

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.nextUnreadChapter)
        assertEquals(2L, viewModel.state.value.nextUnreadChapter?.id)
    }

    // ---- ToggleFavorite ----

    @Test
    fun onEvent_ToggleFavorite_callsRepository() = runTest {
        setUpDefaultMocks()
        coEvery { mangaRepository.toggleFavorite(mangaId) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleFavorite)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mangaRepository.toggleFavorite(mangaId) }
    }

    @Test
    fun onEvent_ToggleFavorite_emitsSnackbarEffect() = runTest {
        setUpDefaultMocks()
        coEvery { mangaRepository.toggleFavorite(mangaId) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ToggleFavorite)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShowSnackbar)
        }
    }

    @Test
    fun onEvent_ToggleFavorite_onError_emitsErrorEffect() = runTest {
        setUpDefaultMocks()
        coEvery { mangaRepository.toggleFavorite(mangaId) } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ToggleFavorite)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShowError)
        }
    }

    @Test
    fun onEvent_ToggleFavorite_addingWithCategories_showsCategoryPicker() = runTest {
        setUpDefaultMocks()
        coEvery { mangaRepository.toggleFavorite(mangaId) } returns Unit
        every { categoryRepository.getCategories() } returns flowOf(
            listOf(Category(id = 1L, name = "Reading"), Category(id = 2L, name = "Plan to Read")),
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleFavorite)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.showCategoryPickerDialog)
        assertEquals(emptySet<Long>(), state.categoryPickerSelection)
    }

    @Test
    fun onEvent_ToggleFavorite_addingWithNoCategories_doesNotShowCategoryPicker() = runTest {
        setUpDefaultMocks()
        coEvery { mangaRepository.toggleFavorite(mangaId) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleFavorite)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showCategoryPickerDialog)
    }

    @Test
    fun onEvent_ToggleFavorite_removingFromLibrary_doesNotShowCategoryPicker() = runTest {
        stubManga(sampleManga.copy(favorite = true))
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(sampleChapters)
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(true)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns sampleChapters[1]
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
        every { categoryRepository.getCategories() } returns flowOf(listOf(Category(id = 1L, name = "Reading")))
        coEvery { mangaRepository.toggleFavorite(mangaId) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleFavorite)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showCategoryPickerDialog)
    }

    @Test
    fun onEvent_ToggleFavorite_removingWithDownloads_showsDeleteDownloadsPrompt() = runTest {
        stubManga(sampleManga.copy(favorite = true))
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(sampleChapters)
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(true)
        every { downloadRepository.observeDownloads() } returns flowOf(
            listOf(
                DownloadItem(
                    id = 1L,
                    mangaId = mangaId,
                    chapterId = sampleChapters[0].id,
                    mangaTitle = sampleManga.title,
                    chapterTitle = sampleChapters[0].name,
                    status = DownloadStatus.COMPLETED,
                )
            )
        )
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns sampleChapters[1]
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        coEvery { mangaRepository.toggleFavorite(mangaId) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ToggleFavorite)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShowDeleteDownloadsPrompt)
        }
    }

    @Test
    fun onEvent_ToggleFavorite_removingWithNoDownloads_showsPlainSnackbar() = runTest {
        stubManga(sampleManga.copy(favorite = true))
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(sampleChapters)
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(true)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns sampleChapters[1]
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        coEvery { mangaRepository.toggleFavorite(mangaId) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ToggleFavorite)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShowSnackbar)
        }
    }

    // ---- Category picker ----

    @Test
    fun onEvent_ToggleCategoryPickerSelection_addsAndRemovesId() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleCategoryPickerSelection(1L))
        assertEquals(setOf(1L), viewModel.state.value.categoryPickerSelection)

        viewModel.onEvent(DetailsContract.Event.ToggleCategoryPickerSelection(1L))
        assertEquals(emptySet<Long>(), viewModel.state.value.categoryPickerSelection)
    }

    @Test
    fun onEvent_ConfirmCategoryPicker_assignsSelectedCategoriesAndClosesDialog() = runTest {
        setUpDefaultMocks()
        coEvery { categoryRepository.addMangaToCategory(any(), any()) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleCategoryPickerSelection(1L))
        viewModel.onEvent(DetailsContract.Event.ToggleCategoryPickerSelection(2L))
        viewModel.onEvent(DetailsContract.Event.ConfirmCategoryPicker)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showCategoryPickerDialog)
        coVerify { categoryRepository.addMangaToCategory(mangaId, 1L) }
        coVerify { categoryRepository.addMangaToCategory(mangaId, 2L) }
    }

    @Test
    fun onEvent_DismissCategoryPicker_closesDialogWithoutAssigning() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleCategoryPickerSelection(1L))
        viewModel.onEvent(DetailsContract.Event.DismissCategoryPicker)

        assertFalse(viewModel.state.value.showCategoryPickerDialog)
        coVerify(exactly = 0) { categoryRepository.addMangaToCategory(any(), any()) }
    }

    // ---- ToggleDescription ----

    @Test
    fun onEvent_ToggleDescription_togglesDescriptionExpanded() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.descriptionExpanded)
        viewModel.onEvent(DetailsContract.Event.ToggleDescription)
        assertTrue(viewModel.state.value.descriptionExpanded)
        viewModel.onEvent(DetailsContract.Event.ToggleDescription)
        assertFalse(viewModel.state.value.descriptionExpanded)
    }

    // ---- ToggleSortOrder ----

    @Test
    fun onEvent_ToggleSortOrder_changesSortOrder() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val initial = viewModel.state.value.chapterSortOrder
        viewModel.onEvent(DetailsContract.Event.ToggleSortOrder)
        val toggled = viewModel.state.value.chapterSortOrder

        assertTrue(initial != toggled)
    }

    @Test
    fun onEvent_ToggleSortOrder_persistsFlagsToRepository() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleSortOrder)
        testDispatcher.scheduler.advanceUntilIdle()

        val expectedFlags = chapterFlagsOf(
            DetailsContract.ChapterSortOrder.ASCENDING,
            DetailsContract.ChapterFilter(),
        )
        coVerify { mangaRepository.updateChapterFlags(mangaId, expectedFlags) }
    }

    @Test
    fun onEvent_SetChapterFilter_persistsFlagsToRepository() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val filter = DetailsContract.ChapterFilter(read = DetailsContract.TriState.EXCLUDE)
        viewModel.onEvent(DetailsContract.Event.SetChapterFilter(filter))
        testDispatcher.scheduler.advanceUntilIdle()

        val expectedFlags = chapterFlagsOf(DetailsContract.ChapterSortOrder.DESCENDING, filter)
        coVerify { mangaRepository.updateChapterFlags(mangaId, expectedFlags) }
    }

    @Test
    fun init_restoresSortOrderAndFilterFromMangaChapterFlags() = runTest {
        val flags = chapterFlagsOf(
            DetailsContract.ChapterSortOrder.ASCENDING,
            DetailsContract.ChapterFilter(downloaded = DetailsContract.TriState.ONLY),
        )
        stubManga(sampleManga.copy(chapterFlags = flags))
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(sampleChapters)
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(false)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns sampleChapters[1]
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(DetailsContract.ChapterSortOrder.ASCENDING, state.chapterSortOrder)
        assertEquals(DetailsContract.TriState.ONLY, state.chapterFilter.downloaded)
    }

    // ---- StartReading ----

    @Test
    fun onEvent_StartReading_withChapters_emitsNavigateEffect() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.StartReading)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.NavigateToReader)
            assertEquals(mangaId, (effect as DetailsContract.Effect.NavigateToReader).mangaId)
        }
    }

    @Test
    fun onEvent_StartReading_withNoChapters_emitsErrorEffect() = runTest {
        stubManga(sampleManga)
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(emptyList())
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(false)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns null
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.StartReading)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShowError)
        }
    }

    // ---- ChapterClick ----

    @Test
    fun onEvent_ChapterClick_emitsNavigateToReaderEffect() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ChapterClick(2L))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.NavigateToReader)
            assertEquals(2L, (effect as DetailsContract.Effect.NavigateToReader).chapterId)
        }
    }

    // ---- ToggleChapterRead ----

    @Test
    fun onEvent_ToggleChapterRead_unreadToRead_updatesProgress() = runTest {
        setUpDefaultMocks()
        coEvery { chapterRepository.updateChapterProgress(any<Long>(), any(), any()) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Chapter 2 is unread initially
        viewModel.onEvent(DetailsContract.Event.ToggleChapterRead(2L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { chapterRepository.updateChapterProgress(2L, true, 0) }
    }

    @Test
    fun onEvent_ToggleChapterRead_readToUnread_updatesProgress() = runTest {
        setUpDefaultMocks()
        coEvery { chapterRepository.updateChapterProgress(any<Long>(), any(), any()) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Chapter 1 is read initially (read = true)
        viewModel.onEvent(DetailsContract.Event.ToggleChapterRead(1L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { chapterRepository.updateChapterProgress(1L, false, any()) }
    }

    // ---- MarkPreviousAsRead ----

    @Test
    fun onEvent_MarkPreviousAsRead_marksAllPreviousChapters() = runTest {
        setUpDefaultMocks()
        coEvery { chapterRepository.updateChapterProgress(any<List<Long>>(), any(), any()) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Mark all chapters before chapter 3 (chapters 1 and 2) as read
        // Chapter 1 is already read, Chapter 2 is not
        viewModel.onEvent(DetailsContract.Event.MarkPreviousAsRead(3L))
        testDispatcher.scheduler.advanceUntilIdle()

        // Only chapter 2 (unread and previous to 3) should be updated
        coVerify { chapterRepository.updateChapterProgress(listOf(2L), true, 0) }
    }

    // ---- ShareManga ----

    @Test
    fun onEvent_ShareManga_withAbsoluteHttpUrl_emitsShareMangaEffectWithUrl() = runTest {
        val mangaWithHttpUrl = sampleManga.copy(url = "http://example.com/manga/42")
        setUpDefaultMocks()
        every { mangaRepository.getMangaByIdFlow(mangaId) } returns flowOf(mangaWithHttpUrl)
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(sampleChapters)
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(false)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns null

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ShareManga)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShareManga)
            val shareEffect = effect as DetailsContract.Effect.ShareManga
            assertEquals("Attack on Titan", shareEffect.title)
            assertEquals("http://example.com/manga/42", shareEffect.url)
        }
    }

    @Test
    fun onEvent_ShareManga_withAbsoluteHttpsUrl_emitsShareMangaEffectWithUrl() = runTest {
        val mangaWithHttpsUrl = sampleManga.copy(url = "https://example.com/manga/42")
        setUpDefaultMocks()
        every { mangaRepository.getMangaByIdFlow(mangaId) } returns flowOf(mangaWithHttpsUrl)
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(sampleChapters)
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(false)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns null

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ShareManga)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShareManga)
            val shareEffect = effect as DetailsContract.Effect.ShareManga
            assertEquals("Attack on Titan", shareEffect.title)
            assertEquals("https://example.com/manga/42", shareEffect.url)
        }
    }

    @Test
    fun onEvent_ShareManga_withRelativeUrl_emitsShareMangaEffectWithEmptyUrl() = runTest {
        // sampleManga has url = "/m/42" which is a relative path
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ShareManga)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShareManga)
            val shareEffect = effect as DetailsContract.Effect.ShareManga
            assertEquals("Attack on Titan", shareEffect.title)
            assertEquals("", shareEffect.url)
        }
    }

    @Test
    fun onEvent_ShareManga_whenMangaIsNull_emitsNoEffect() = runTest {
        stubManga(null)
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(emptyList())
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(false)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns null
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ShareManga)
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }
    }

    // ---- ToggleNotifications ----

    @Test
    fun onEvent_ToggleNotifications_disablesNotifications_whenCurrentlyEnabled() = runTest {
        setUpDefaultMocks()
        // sampleManga has notifyNewChapters = true
        coEvery { setMangaNotifications(mangaId, false) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ToggleNotifications)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { setMangaNotifications(mangaId, false) }
            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShowSnackbar)
            assertTrue((effect as DetailsContract.Effect.ShowSnackbar).message.contains("muted", ignoreCase = true))
        }
    }

    @Test
    fun onEvent_ToggleNotifications_enablesNotifications_whenCurrentlyMuted() = runTest {
        val mutedManga = sampleManga.copy(notifyNewChapters = false)
        stubManga(mutedManga)
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(sampleChapters)
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(false)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns sampleChapters[1]
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
        coEvery { setMangaNotifications(mangaId, true) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ToggleNotifications)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { setMangaNotifications(mangaId, true) }
            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShowSnackbar)
            assertTrue((effect as DetailsContract.Effect.ShowSnackbar).message.contains("enabled", ignoreCase = true))
        }
    }

    @Test
    fun onEvent_ToggleNotifications_onError_emitsErrorEffect() = runTest {
        setUpDefaultMocks()
        coEvery { setMangaNotifications(any(), any()) } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.ToggleNotifications)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.ShowError)
        }
    }

    // ---- State derived properties ----

    @Test
    fun state_canStartReading_withChapters_isTrue() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.canStartReading)
    }

    @Test
    fun state_hasUnreadChapters_withUnreadChapters_isTrue() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.hasUnreadChapters)
    }

    @Test
    fun state_sortedChapters_descending_sortsHighestFirst() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Default is DESCENDING
        val sorted = viewModel.state.value.sortedChapters
        assertTrue(sorted[0].chapterNumber >= sorted[1].chapterNumber)
    }

    // ---- SearchGlobally (title/author/artist tap, and genre long-press) ----

    @Test
    fun onEvent_SearchGlobally_emitsNavigateToGlobalSearchEffect() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.SearchGlobally("Attack on Titan"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.NavigateToGlobalSearch)
            assertEquals("Attack on Titan", (effect as DetailsContract.Effect.NavigateToGlobalSearch).query)
        }
    }

    @Test
    fun onEvent_SearchGlobally_withBlankQuery_doesNotEmitEffect() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.SearchGlobally("   "))
            expectNoEvents()
        }
    }

    @Test
    fun onEvent_GenreLongClick_emitsNavigateToGlobalSearchEffect() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.GenreLongClick("Shounen"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.NavigateToGlobalSearch)
            assertEquals("Shounen", (effect as DetailsContract.Effect.NavigateToGlobalSearch).query)
        }
    }

    @Test
    fun onEvent_SourceClick_emitsNavigateToSourceSearchWithEmptyQuery() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.SourceClick)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.NavigateToSourceSearch)
            val navigate = effect as DetailsContract.Effect.NavigateToSourceSearch
            assertEquals(sampleManga.sourceId.toString(), navigate.sourceId)
            assertEquals("", navigate.query)
        }
    }

    @Test
    fun onEvent_MigrateManga_emitsNavigateToMigrationWithMangaId() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.MigrateManga)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.NavigateToMigration)
            assertEquals(mangaId, (effect as DetailsContract.Effect.NavigateToMigration).mangaId)
        }
    }

    // ---- DeleteChapterDownload (cancel while downloading vs delete once downloaded) ----

    @Test
    fun onEvent_DeleteChapterDownload_whileDownloading_cancelsInsteadOfDeletingFiles() = runTest {
        setUpDefaultMocks()
        val downloadingChapterId = sampleChapters[1].id
        every { downloadRepository.observeDownloads() } returns flowOf(
            listOf(
                DownloadItem(
                    id = 1L,
                    mangaId = mangaId,
                    chapterId = downloadingChapterId,
                    mangaTitle = sampleManga.title,
                    chapterTitle = "Chapter 2",
                    status = DownloadStatus.DOWNLOADING,
                )
            )
        )
        coEvery { downloadRepository.cancelDownload(downloadingChapterId) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.DeleteChapterDownload(downloadingChapterId))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { downloadRepository.cancelDownload(downloadingChapterId) }
        coVerify(exactly = 0) { downloadRepository.deleteChapterDownload(any(), any(), any(), any()) }
    }

    @Test
    fun onEvent_DeleteChapterDownload_whenDownloaded_deletesFilesInsteadOfCancelling() = runTest {
        setUpDefaultMocks()
        val downloadedChapterId = sampleChapters[1].id
        every { downloadRepository.observeDownloads() } returns flowOf(
            listOf(
                DownloadItem(
                    id = 1L,
                    mangaId = mangaId,
                    chapterId = downloadedChapterId,
                    mangaTitle = sampleManga.title,
                    chapterTitle = "Chapter 2",
                    status = DownloadStatus.COMPLETED,
                )
            )
        )
        coEvery {
            downloadRepository.deleteChapterDownload(downloadedChapterId, any(), any(), any())
        } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.DeleteChapterDownload(downloadedChapterId))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { downloadRepository.deleteChapterDownload(downloadedChapterId, any(), any(), any()) }
        coVerify(exactly = 0) { downloadRepository.cancelDownload(any()) }
    }

    // ---- Reading list picker ----

    @Test
    fun onEvent_ShowReadingListPicker_seedsSelectionFromCurrentMembership() = runTest {
        setUpDefaultMocks()
        val existingList = app.otakureader.domain.model.ReadingList(id = 5L, name = "Currently Reading")
        every { readingListRepository.getAllLists() } returns flowOf(listOf(existingList))
        coEvery { readingListRepository.getListsForManga(mangaId) } returns flowOf(
            listOf(app.otakureader.domain.model.ReadingListItem(listId = 5L, mangaId = mangaId))
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ShowReadingListPicker)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.showReadingListPickerDialog)
        assertEquals(setOf(5L), state.readingListPickerSelection)
    }

    @Test
    fun onEvent_ToggleReadingListPickerSelection_addsMangaWhenNotSelected() = runTest {
        setUpDefaultMocks()
        coEvery { readingListRepository.getListsForManga(mangaId) } returns flowOf(emptyList())
        coEvery { readingListRepository.addMangaToList(5L, mangaId) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(DetailsContract.Event.ShowReadingListPicker)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleReadingListPickerSelection(5L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(5L in viewModel.state.value.readingListPickerSelection)
        coVerify(exactly = 1) { readingListRepository.addMangaToList(5L, mangaId) }
    }

    @Test
    fun onEvent_ToggleReadingListPickerSelection_removesMangaWhenAlreadySelected() = runTest {
        setUpDefaultMocks()
        coEvery { readingListRepository.getListsForManga(mangaId) } returns flowOf(
            listOf(app.otakureader.domain.model.ReadingListItem(listId = 5L, mangaId = mangaId))
        )
        coEvery { readingListRepository.removeMangaFromList(5L, mangaId) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(DetailsContract.Event.ShowReadingListPicker)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.ToggleReadingListPickerSelection(5L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(5L in viewModel.state.value.readingListPickerSelection)
        coVerify(exactly = 1) { readingListRepository.removeMangaFromList(5L, mangaId) }
    }

    // ---- Error-state "try in browser" fallback ----

    @Test
    fun onEvent_OpenWebViewFallback_emitsNavigateEffectWithWebUrl() = runTest {
        setUpDefaultMocks()
        val source = mockk<app.otakureader.sourceapi.MangaSource>(relaxed = true)
        every { source.baseUrl } returns "https://example.com"
        coEvery { sourceRepository.getSource(sampleManga.sourceId.toString()) } returns source

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.OpenWebViewFallback)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DetailsContract.Effect.NavigateToWebViewFallback)
            assertEquals(
                "https://example.com/m/42",
                (effect as DetailsContract.Effect.NavigateToWebViewFallback).url,
            )
        }
    }

    @Test
    fun onEvent_OpenWebViewFallback_doesNothingWhenWebUrlUnavailable() = runTest {
        setUpDefaultMocks()
        // Default relaxed sourceRepository mock resolves an empty baseUrl, so mangaWebUrl stays null.

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.mangaWebUrl)
        viewModel.effect.test {
            viewModel.onEvent(DetailsContract.Event.OpenWebViewFallback)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    // ---- Tracker entries + AniList metadata (Stage 5b) ----

    private fun trackEntry(
        trackerId: Int,
        remoteId: Long,
        status: TrackStatus = TrackStatus.READING,
        lastChapterRead: Float = 12f,
        totalChapters: Int = 45,
        score: Float = 8.5f,
    ) = TrackEntry(
        remoteId = remoteId,
        mangaId = mangaId,
        trackerId = trackerId,
        status = status,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        score = score,
    )

    private val sampleMetadata = MangaMetadata(
        mangaId = mangaId,
        anilistId = 53390L,
        description = "AniList synopsis",
        genres = listOf("Action", "Drama"),
        tags = listOf(MangaMetadataTag("Isekai", 87)),
        averageScore = 84,
    )

    @Test
    fun init_keepsTrackEntriesAndDerivesTrackingCountFromThem() = runTest {
        setUpDefaultMocks()
        val entries = listOf(
            trackEntry(TrackerType.ANILIST, remoteId = 53390L),
            trackEntry(TrackerType.MY_ANIME_LIST, remoteId = 777L),
        )
        every { trackRepository.observeEntriesForManga(mangaId) } returns flowOf(entries)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        // The entries themselves are what the chips render; the count is now derived, not stored.
        assertEquals(entries, state.trackEntries)
        assertEquals(2, state.trackingCount)
        assertEquals(53390L, state.anilistMediaId)
    }

    @Test
    fun init_exposesTrackerNamesFromInjectedTrackers() = runTest {
        setUpDefaultMocks()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("AniList", viewModel.state.value.trackerNames[TrackerType.ANILIST])
    }

    @Test
    fun init_refreshesMetadataUsingTheAniListEntryRemoteIdAsMediaId() = runTest {
        setUpDefaultMocks()
        every { trackRepository.observeEntriesForManga(mangaId) } returns flowOf(
            listOf(trackEntry(TrackerType.ANILIST, remoteId = 53390L))
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // The AniList tracker's remoteId *is* the AniList media id, so no matching step is needed.
        coVerify(exactly = 1) { metadataRepository.refreshMetadata(mangaId, 53390L, false) }
        // A finished refresh must leave the spinner off, not just return.
        assertFalse(viewModel.state.value.isMetadataLoading)
    }

    @Test
    fun init_doesNotRefreshMetadataWhenNoAniListEntryExists() = runTest {
        setUpDefaultMocks()
        every { trackRepository.observeEntriesForManga(mangaId) } returns flowOf(
            listOf(trackEntry(TrackerType.MY_ANIME_LIST, remoteId = 777L))
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { metadataRepository.refreshMetadata(any(), any(), any()) }
        assertFalse(viewModel.state.value.isMetadataLoading)
    }

    @Test
    fun observeTrackEntries_refreshesOnceAcrossRepeatedEmissionsOfTheSameMediaId() = runTest {
        setUpDefaultMocks()
        // The entries flow re-emits on every tracker write — pushing chapter progress after a read
        // is enough. Without the once-per-id guard each of those would launch another refresh.
        val entry = trackEntry(TrackerType.ANILIST, remoteId = 53390L)
        every { trackRepository.observeEntriesForManga(mangaId) } returns flowOf(
            listOf(entry),
            listOf(entry.copy(lastChapterRead = 13f)),
            listOf(entry.copy(lastChapterRead = 14f)),
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { metadataRepository.refreshMetadata(mangaId, 53390L, false) }
        // The later emissions still have to reach state — the guard suppresses the fetch, not the
        // entries themselves.
        assertEquals(14f, viewModel.state.value.trackEntries.single().lastChapterRead, 0f)
    }

    @Test
    fun observeTrackEntries_lastAniListLinkWinsAndCancelsTheStaleFetch() = runTest {
        setUpDefaultMocks()
        // Correcting a wrong link changes the remoteId while the mangaId stays put. Two things
        // have to hold: the new id gets past the per-id guard, and the fetch for the *old* id is
        // abandoned. Without the second, a slow stale fetch lands after the fresh one and writes
        // the wrong manga's metadata into the cache — the same "slower writer wins" race the
        // repository's per-manga lock cannot decide, because ordering is what's at stake, not
        // mutual exclusion.
        var staleFetchCompleted = false
        coEvery { metadataRepository.refreshMetadata(mangaId, 53390L, false) } coAnswers {
            delay(SLOW_FETCH_MS)
            staleFetchCompleted = true
            Result.success(sampleMetadata)
        }
        every { trackRepository.observeEntriesForManga(mangaId) } returns flowOf(
            listOf(trackEntry(TrackerType.ANILIST, remoteId = 53390L)),
            listOf(trackEntry(TrackerType.ANILIST, remoteId = 99999L)),
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { metadataRepository.refreshMetadata(mangaId, 99999L, false) }
        assertFalse(staleFetchCompleted)
        assertEquals(99999L, viewModel.state.value.anilistMediaId)
        assertFalse(viewModel.state.value.isMetadataLoading)
    }

    @Test
    fun onEvent_Refresh_forcesAMetadataRefetch() = runTest {
        setUpDefaultMocks()
        every { trackRepository.observeEntriesForManga(mangaId) } returns flowOf(
            listOf(trackEntry(TrackerType.ANILIST, remoteId = 53390L))
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DetailsContract.Event.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        // force = true is what makes pull-to-refresh a real retry: it bypasses both the
        // once-per-id guard here and the repository's TTL.
        coVerify(exactly = 1) { metadataRepository.refreshMetadata(mangaId, 53390L, true) }
    }

    @Test
    fun observeMetadata_putsCachedMetadataIntoState() = runTest {
        setUpDefaultMocks()
        every { metadataRepository.observeMetadata(mangaId) } returns flowOf(sampleMetadata)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(sampleMetadata, viewModel.state.value.metadata)
    }

    @Test
    fun observeMetadata_survivesAFailedRefresh() = runTest {
        setUpDefaultMocks()
        every { metadataRepository.observeMetadata(mangaId) } returns flowOf(sampleMetadata)
        every { trackRepository.observeEntriesForManga(mangaId) } returns flowOf(
            listOf(trackEntry(TrackerType.ANILIST, remoteId = 53390L))
        )
        coEvery { metadataRepository.refreshMetadata(mangaId, 53390L, false) } returns
            Result.failure(IllegalStateException("AniList is down"))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Cache-first: a failed fetch must leave what was already cached on screen, and must not
        // strand the spinner. Asserting the state left behind, not just that the call returned.
        assertEquals(sampleMetadata, viewModel.state.value.metadata)
        assertFalse(viewModel.state.value.isMetadataLoading)
    }

    private companion object {
        /** Long enough that a fetch left running would obviously outlive the one that replaced it. */
        const val SLOW_FETCH_MS = 5_000L
    }
}
