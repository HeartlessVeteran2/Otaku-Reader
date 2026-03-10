package app.otakureader.feature.details

import androidx.lifecycle.SavedStateHandle
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.usecase.SetMangaNotificationsUseCase
import app.otakureader.domain.usecase.UpdateMangaNoteUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mangaId = 42L

    private lateinit var mangaRepository: MangaRepository
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var updateMangaNote: UpdateMangaNoteUseCase
    private lateinit var setMangaNotifications: SetMangaNotificationsUseCase
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
        downloadRepository = mockk()
        downloadPreferences = mockk()
        updateMangaNote = mockk()
        setMangaNotifications = mockk()
        savedStateHandle = SavedStateHandle(mapOf(DetailsViewModel.MANGA_ID_ARG to mangaId))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DetailsViewModel {
        return DetailsViewModel(savedStateHandle, mangaRepository, chapterRepository, downloadRepository, downloadPreferences, updateMangaNote, setMangaNotifications)
    }

    private fun setUpDefaultMocks() {
        every { mangaRepository.getMangaByIdFlow(mangaId) } returns flowOf(sampleManga)
        every { chapterRepository.getChaptersByMangaId(mangaId) } returns flowOf(sampleChapters)
        every { mangaRepository.isFavorite(mangaId) } returns flowOf(false)
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())
        coEvery { chapterRepository.getNextUnreadChapter(mangaId) } returns sampleChapters[1]
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
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
        every { mangaRepository.getMangaByIdFlow(mangaId) } returns flowOf(sampleManga)
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
        every { mangaRepository.getMangaByIdFlow(mangaId) } returns flowOf(null)
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
        every { mangaRepository.getMangaByIdFlow(mangaId) } returns flowOf(mutedManga)
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
}
