package app.otakureader.feature.reader.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.otakureader.data.loader.PageLoader
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.repository.ReaderSettingsRepository
import io.mockk.coEvery
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mangaRepository = mockk()
        chapterRepository = mockk()
        settingsRepository = mockk()
        pageLoader = mockk()

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
}
