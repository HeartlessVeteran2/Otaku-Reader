package app.otakureader.feature.reader.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.otakureader.data.loader.PageLoader
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.core.discord.DiscordRpcService
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.feature.reader.model.ColorFilterMode
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.repository.ReaderSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
class UltimateReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mangaId = 1L
    private val chapterId = 10L

    private lateinit var mangaRepository: MangaRepository
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var settingsRepository: ReaderSettingsRepository
    private lateinit var pageLoader: PageLoader
    private lateinit var discordRpcService: DiscordRpcService
    private lateinit var generalPreferences: GeneralPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mangaRepository = mockk()
        chapterRepository = mockk()
        settingsRepository = mockk()
        pageLoader = mockk()
        discordRpcService = mockk(relaxed = true)
        generalPreferences = mockk()
        every { generalPreferences.discordRpcEnabled } returns flowOf(false)

        // Default settings stubs so loadSettings() succeeds.
        every { settingsRepository.readerMode } returns flowOf(ReaderMode.SINGLE_PAGE)
        every { settingsRepository.brightness } returns flowOf(1f)
        every { settingsRepository.keepScreenOn } returns flowOf(true)
        every { settingsRepository.showPageNumber } returns flowOf(true)
        every { settingsRepository.readingDirection } returns flowOf(ReadingDirection.LTR)
        every { settingsRepository.volumeKeysEnabled } returns flowOf(false)
        every { settingsRepository.volumeKeysInverted } returns flowOf(false)
        every { settingsRepository.fullscreen } returns flowOf(true)
        every { settingsRepository.incognitoMode } returns flowOf(false)
        every { settingsRepository.colorFilterMode } returns flowOf(ColorFilterMode.NONE)
        every { settingsRepository.customTintColor } returns flowOf(0x4000AAFFL)
        every { settingsRepository.preloadPagesBefore } returns flowOf(ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES)
        every { settingsRepository.preloadPagesAfter } returns flowOf(ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES)

        // Return null for chapter/manga so loadChapter() exits early without side-effects.
        coEvery { chapterRepository.getChapterById(chapterId) } returns null
        coEvery { mangaRepository.getMangaById(mangaId) } returns null

        // Stub write operations called by page-change / jump-to-page paths.
        coEvery { chapterRepository.updateChapterProgress(any<Long>(), any<Boolean>(), any<Int>()) } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UltimateReaderViewModel =
        UltimateReaderViewModel(
            mangaRepository = mangaRepository,
            chapterRepository = chapterRepository,
            settingsRepository = settingsRepository,
            pageLoader = pageLoader,
            discordRpcService = discordRpcService,
            generalPreferences = generalPreferences,
            savedStateHandle = SavedStateHandle(
                mapOf("mangaId" to mangaId, "chapterId" to chapterId)
            )
        )

    // ---- Gallery toggle ----

    @Test
    fun `gallery is closed by default`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isGalleryOpen)
    }

    @Test
    fun `ToggleGallery opens the gallery when it is closed`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ReaderEvent.ToggleGallery)

        assertTrue(vm.state.value.isGalleryOpen)
    }

    @Test
    fun `ToggleGallery closes the gallery when it is open`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ReaderEvent.ToggleGallery)
        assertTrue(vm.state.value.isGalleryOpen)

        vm.onEvent(ReaderEvent.ToggleGallery)
        assertFalse(vm.state.value.isGalleryOpen)
    }

    // ---- Jump to page ----

    @Test
    fun `jumpToPage closes the gallery`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Inject pages so navigation has something to work with.
        vm.setPages(List(20) { ReaderPage(index = it) })

        vm.onEvent(ReaderEvent.ToggleGallery)
        assertTrue(vm.state.value.isGalleryOpen)

        vm.jumpToPage(5)

        assertFalse(vm.state.value.isGalleryOpen)
    }

    @Test
    fun `jumpToPage navigates to the requested page`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setPages(List(20) { ReaderPage(index = it) })
        vm.jumpToPage(7)

        assertEquals(7, vm.state.value.currentPage)
    }

    // ---- Gallery columns ----

    @Test
    fun `galleryColumns defaults to 3`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, vm.state.value.galleryColumns)
    }

    @Test
    fun `SetGalleryColumns updates the column count`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ReaderEvent.SetGalleryColumns(2))
        assertEquals(2, vm.state.value.galleryColumns)

        vm.onEvent(ReaderEvent.SetGalleryColumns(4))
        assertEquals(4, vm.state.value.galleryColumns)
    }

    @Test
    fun `SetGalleryColumns clamps values below 2 to 2`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ReaderEvent.SetGalleryColumns(1))
        assertEquals(2, vm.state.value.galleryColumns)

        vm.onEvent(ReaderEvent.SetGalleryColumns(0))
        assertEquals(2, vm.state.value.galleryColumns)

        vm.onEvent(ReaderEvent.SetGalleryColumns(-1))
        assertEquals(2, vm.state.value.galleryColumns)
    }

    @Test
    fun `SetGalleryColumns clamps values above 4 to 4`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ReaderEvent.SetGalleryColumns(10))
        assertEquals(4, vm.state.value.galleryColumns)
    }

    // ---- Color filter ----

    @Test
    fun `colorFilterMode defaults to NONE`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ColorFilterMode.NONE, vm.state.value.colorFilterMode)
    }

    @Test
    fun `SetColorFilterMode updates colorFilterMode state`() = runTest {
        coEvery { settingsRepository.setColorFilterMode(any()) } just runs

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ReaderEvent.SetColorFilterMode(ColorFilterMode.SEPIA))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ColorFilterMode.SEPIA, vm.state.value.colorFilterMode)

        vm.onEvent(ReaderEvent.SetColorFilterMode(ColorFilterMode.GRAYSCALE))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ColorFilterMode.GRAYSCALE, vm.state.value.colorFilterMode)

        vm.onEvent(ReaderEvent.SetColorFilterMode(ColorFilterMode.INVERT))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ColorFilterMode.INVERT, vm.state.value.colorFilterMode)

        vm.onEvent(ReaderEvent.SetColorFilterMode(ColorFilterMode.CUSTOM_TINT))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ColorFilterMode.CUSTOM_TINT, vm.state.value.colorFilterMode)
    }

    @Test
    fun `SetColorFilterMode back to NONE clears filter`() = runTest {
        coEvery { settingsRepository.setColorFilterMode(any()) } just runs

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ReaderEvent.SetColorFilterMode(ColorFilterMode.SEPIA))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ColorFilterMode.SEPIA, vm.state.value.colorFilterMode)

        vm.onEvent(ReaderEvent.SetColorFilterMode(ColorFilterMode.NONE))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ColorFilterMode.NONE, vm.state.value.colorFilterMode)
    }

    @Test
    fun `SetCustomTintColor updates customTintColor state`() = runTest {
        coEvery { settingsRepository.setColorFilterMode(any()) } just runs
        coEvery { settingsRepository.setCustomTintColor(any()) } just runs

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val newColor = 0x80FF6B6BL
        vm.onEvent(ReaderEvent.SetCustomTintColor(newColor))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(newColor, vm.state.value.customTintColor)
    }

    // ---- Reader background color ----

    @Test
    fun `SetReaderBackgroundColor updates state`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.readerBackgroundColor)

        vm.onEvent(ReaderEvent.SetReaderBackgroundColor(0xFF1A1A1AL))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0xFF1A1A1AL, vm.state.value.readerBackgroundColor)
    }

    @Test
    fun `SetReaderBackgroundColor with null resets to default`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ReaderEvent.SetReaderBackgroundColor(0xFFF5E6CCL))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0xFFF5E6CCL, vm.state.value.readerBackgroundColor)

        vm.onEvent(ReaderEvent.SetReaderBackgroundColor(null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.readerBackgroundColor)
    }

    @Test
    fun `SetReaderBackgroundColor persists to repository when manga is loaded`() = runTest {
        // Provide a real chapter and manga so loadChapter() populates currentManga.
        val chapter = Chapter(
            id = chapterId, mangaId = mangaId, url = "/ch/1", name = "Chapter 1"
        )
        val manga = Manga(
            id = mangaId, sourceId = 1L, url = "/m/1", title = "Test Manga"
        )
        coEvery { chapterRepository.getChapterById(chapterId) } returns chapter
        coEvery { mangaRepository.getMangaById(mangaId) } returns manga
        coEvery { mangaRepository.updateManga(any()) } just runs
        coEvery { chapterRepository.recordHistory(any(), any(), any()) } just runs

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ReaderEvent.SetReaderBackgroundColor(0xFF333333L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0xFF333333L, vm.state.value.readerBackgroundColor)
        coVerify {
            mangaRepository.updateManga(match { it.readerBackgroundColor == 0xFF333333L })
        }
    }

    // ---- Preload settings (#264) ----

    @Test
    fun `preloadPages uses global defaults from ReaderSettingsRepository`() = runTest {
        // Set custom global preload settings
        every { settingsRepository.preloadPagesBefore } returns flowOf(5)
        every { settingsRepository.preloadPagesAfter } returns flowOf(7)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Set pages and navigate to trigger preloading
        vm.setPages(List(20) { ReaderPage(index = it) })
        vm.jumpToPage(10)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(10, vm.state.value.currentPage)
        // Verify preload settings flows were collected during loadSettings()
        io.mockk.verify { settingsRepository.preloadPagesBefore }
        io.mockk.verify { settingsRepository.preloadPagesAfter }
    }

    @Test
    fun `preloadPages falls back to defaults when settings read fails`() = runTest {
        // Simulate settings read failure by returning a throwing flow
        every { settingsRepository.preloadPagesBefore } returns kotlinx.coroutines.flow.flow { throw RuntimeException("Read failed") }
        every { settingsRepository.preloadPagesAfter } returns kotlinx.coroutines.flow.flow { throw RuntimeException("Read failed") }

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setPages(List(20) { ReaderPage(index = it) })
        vm.jumpToPage(5)
        testDispatcher.scheduler.advanceUntilIdle()

        // Navigation should still succeed even when preload settings fail to load
        assertEquals(5, vm.state.value.currentPage)
        // Verify the flows were attempted to be read
        io.mockk.verify { settingsRepository.preloadPagesBefore }
        io.mockk.verify { settingsRepository.preloadPagesAfter }
    }
}
