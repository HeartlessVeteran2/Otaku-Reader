package app.otakureader.feature.more.bookmarks

import android.content.Context
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.PageBookmark
import app.otakureader.domain.bookmark.BookmarkPageExporter
import app.otakureader.domain.model.BookmarkPageRef
import app.otakureader.domain.model.ExportResult
import app.otakureader.domain.model.ShareResult
import app.otakureader.domain.repository.BookmarkCollectionRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.PageBookmarkRepository
import app.otakureader.domain.model.Chapter
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.slot
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BookmarksViewModel].
 *
 * All suspend/Flow interactions are tested with [runTest] and Turbine's [test] extension.
 * External dependencies are mocked with MockK — no Room DB is needed here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val context: Context = mockk(relaxed = true)
    private val pageBookmarkRepository: PageBookmarkRepository = mockk(relaxed = true)
    private val bookmarkCollectionRepository: BookmarkCollectionRepository = mockk()
    private val mangaRepository: MangaRepository = mockk(relaxed = true)
    private val chapterRepository: ChapterRepository = mockk(relaxed = true)
    private val bookmarkPageExporter: BookmarkPageExporter = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // getAllCollections must emit so the combine() in the ViewModel can produce state.
        every { bookmarkCollectionRepository.getAllCollections() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun bookmark(
        id: Long = 1L,
        mangaId: Long = 10L,
        chapterId: Long = 100L,
        pageIndex: Int = 0,
        note: String? = null,
    ) = PageBookmark(
        id = id,
        mangaId = mangaId,
        chapterId = chapterId,
        pageIndex = pageIndex,
        note = note,
    )

    private fun manga(id: Long = 10L, title: String = "Test Manga") =
        Manga(id = id, sourceId = 1L, url = "/m/$id", title = title, thumbnailUrl = "https://cover/$id.jpg")

    private fun chapter(id: Long = 100L, mangaId: Long = 10L, name: String = "Chapter 1") =
        Chapter(id = id, mangaId = mangaId, url = "/ch/$id", name = name)

    private fun createViewModel() = BookmarksViewModel(
        context = context,
        pageBookmarkRepository = pageBookmarkRepository,
        bookmarkCollectionRepository = bookmarkCollectionRepository,
        mangaRepository = mangaRepository,
        chapterRepository = chapterRepository,
        bookmarkPageExporter = bookmarkPageExporter,
    )

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    fun `initial state is loading`() {
        every { pageBookmarkRepository.getAllBookmarks() } returns flowOf(emptyList())
        val vm = createViewModel()
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun `empty bookmark list transitions to not-loading isEmpty state`() = runTest {
        every { pageBookmarkRepository.getAllBookmarks() } returns flowOf(emptyList())
        coEvery { mangaRepository.getMangaByIds(any()) } returns emptyList()

        val vm = createViewModel()
        vm.state.test {
            awaitItem() // initial loading
            val ready = awaitItem()
            assertFalse(ready.isLoading)
            assertTrue(ready.isEmpty)
        }
    }

    @Test
    fun `bookmarks are enriched with manga title and chapter name`() = runTest {
        val bm = bookmark()
        every { pageBookmarkRepository.getAllBookmarks() } returns flowOf(listOf(bm))
        coEvery { mangaRepository.getMangaByIds(listOf(10L)) } returns listOf(manga())
        coEvery { chapterRepository.getChapterById(100L) } returns chapter()

        val vm = createViewModel()
        vm.state.test {
            awaitItem() // initial
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(1, state.bookmarks.size)
            assertEquals("Test Manga", state.bookmarks[0].mangaTitle)
            assertEquals("Chapter 1", state.bookmarks[0].chapterName)
            assertEquals("https://cover/10.jpg", state.bookmarks[0].mangaCoverUrl)
        }
    }

    @Test
    fun `search query filters by manga title`() = runTest {
        val bm1 = bookmark(id = 1L, mangaId = 10L, chapterId = 100L)
        val bm2 = bookmark(id = 2L, mangaId = 20L, chapterId = 200L)
        every { pageBookmarkRepository.getAllBookmarks() } returns flowOf(listOf(bm1, bm2))
        coEvery { mangaRepository.getMangaByIds(any()) } returns listOf(
            manga(id = 10L, title = "Dragon Ball"),
            manga(id = 20L, title = "One Piece"),
        )
        coEvery { chapterRepository.getChapterById(100L) } returns chapter(id = 100L, mangaId = 10L)
        coEvery { chapterRepository.getChapterById(200L) } returns chapter(id = 200L, mangaId = 20L)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(BookmarksIntent.SearchQueryChanged("Dragon"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.filteredBookmarks.size)
        assertEquals("Dragon Ball", state.filteredBookmarks[0].mangaTitle)
    }

    @Test
    fun `toggle manga expanded adds to and removes from collapsedManga`() = runTest {
        every { pageBookmarkRepository.getAllBookmarks() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        // Initially expanded (not in collapsed set).
        assertFalse(10L in vm.state.value.collapsedManga)

        // Collapse.
        vm.onIntent(BookmarksIntent.ToggleMangaExpanded(10L))
        advanceUntilIdle()
        assertTrue(10L in vm.state.value.collapsedManga)

        // Re-expand.
        vm.onIntent(BookmarksIntent.ToggleMangaExpanded(10L))
        advanceUntilIdle()
        assertFalse(10L in vm.state.value.collapsedManga)
    }

    @Test
    fun `delete bookmark intent calls repository removeBookmark`() = runTest {
        val bm = bookmark(chapterId = 100L, pageIndex = 3)
        every { pageBookmarkRepository.getAllBookmarks() } returns flowOf(listOf(bm))
        coEvery { mangaRepository.getMangaByIds(any()) } returns listOf(manga())
        coEvery { chapterRepository.getChapterById(any()) } returns chapter()
        coEvery { pageBookmarkRepository.removeBookmark(any(), any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        val item = vm.state.value.bookmarks.first()
        vm.onIntent(BookmarksIntent.DeleteBookmark(item))
        advanceUntilIdle()

        coVerify { pageBookmarkRepository.removeBookmark(100L, 3) }
    }

    @Test
    fun `open bookmark intent emits NavigateToReader effect`() = runTest {
        every { pageBookmarkRepository.getAllBookmarks() } returns flowOf(emptyList())

        val vm = createViewModel()

        vm.effect.test {
            vm.onIntent(BookmarksIntent.OpenBookmark(mangaId = 10L, chapterId = 100L, pageIndex = 4))
            val effect = awaitItem()
            assertTrue(effect is BookmarksEffect.NavigateToReader)
            effect as BookmarksEffect.NavigateToReader
            assertEquals(10L, effect.mangaId)
            assertEquals(100L, effect.chapterId)
            // The page is the point of a page bookmark: without it the reader opens at
            // `lastPageRead`, which is where the user stopped, not the panel they saved.
            assertEquals(4, effect.pageIndex)
        }
    }

    @Test
    fun `grouped property produces correct BookmarkGroup structure`() = runTest {
        val bm1 = bookmark(id = 1L, mangaId = 10L, chapterId = 100L, pageIndex = 0)
        val bm2 = bookmark(id = 2L, mangaId = 10L, chapterId = 100L, pageIndex = 2)
        val bm3 = bookmark(id = 3L, mangaId = 10L, chapterId = 200L, pageIndex = 1)
        every { pageBookmarkRepository.getAllBookmarks() } returns flowOf(listOf(bm1, bm2, bm3))
        coEvery { mangaRepository.getMangaByIds(listOf(10L)) } returns listOf(manga())
        coEvery { chapterRepository.getChapterById(100L) } returns chapter(id = 100L, name = "Ch 1")
        coEvery { chapterRepository.getChapterById(200L) } returns chapter(id = 200L, name = "Ch 2")

        val vm = createViewModel()
        vm.state.test {
            awaitItem()
            val state = awaitItem()
            val groups = state.grouped
            assertEquals(1, groups.size)
            assertEquals("Test Manga", groups[0].mangaTitle)
            assertEquals(2, groups[0].chapters.size)
            val ch1 = groups[0].chapters.first { it.chapterId == 100L }
            assertEquals(2, ch1.bookmarks.size)
            // Pages within a chapter are sorted by pageIndex.
            assertEquals(0, ch1.bookmarks[0].pageIndex)
            assertEquals(2, ch1.bookmarks[1].pageIndex)
        }
    }

    // ── export / share (#1132, #1133) ────────────────────────────────────────

    /**
     * Selecting needs the enriched list to have arrived first — it is produced asynchronously —
     * and [advanceUntilIdle] afterwards so `state` republishes with the selection applied.
     */
    private fun TestScope.selectFirstBookmark(vm: BookmarksViewModel): BookmarkItem {
        val item = vm.state.value.bookmarks.first()
        vm.onIntent(BookmarksIntent.ToggleBookmarkSelection(item.id))
        advanceUntilIdle()
        return item
    }

    private fun seedOneBookmark() {
        every { pageBookmarkRepository.getAllBookmarks() } returns flowOf(listOf(bookmark()))
        coEvery { mangaRepository.getMangaByIds(listOf(10L)) } returns listOf(manga())
        coEvery { chapterRepository.getChapterById(100L) } returns chapter()
    }

    /**
     * The point of #1132: the export must reach the exporter with the page's real coordinates.
     *
     * Before this, `ExportSelected` sent an effect the Screen answered with a snackbar reading
     * "image export coming in v1.1" — a live button wired to nothing. Asserting the *argument*,
     * not merely that something was called, is what makes this test notice a regression back to
     * passing the wrong ids.
     */
    @Test
    fun `export sends the selected page's coordinates to the exporter`() = runTest {
        seedOneBookmark()
        val refs = slot<List<BookmarkPageRef>>()
        coEvery { bookmarkPageExporter.exportToGallery(capture(refs)) } returns
            ExportResult.Completed(saved = 1, failed = 0)
        val vm = createViewModel()
        advanceUntilIdle()
        selectFirstBookmark(vm)

        vm.onIntent(BookmarksIntent.ExportSelected)
        advanceUntilIdle()

        val ref = refs.captured.single()
        assertEquals(10L, ref.mangaId)
        assertEquals(100L, ref.chapterId)
        assertEquals(0, ref.pageIndex)
        assertEquals("Test Manga", ref.mangaTitle)
        assertEquals("Chapter 1", ref.chapterName)
    }

    /** A partial export reports both halves, so pages that never arrived are not hidden. */
    @Test
    fun `export emits ExportComplete carrying the failure count`() = runTest {
        seedOneBookmark()
        coEvery { bookmarkPageExporter.exportToGallery(any()) } returns
            ExportResult.Completed(saved = 3, failed = 2)
        val vm = createViewModel()
        advanceUntilIdle()
        selectFirstBookmark(vm)

        vm.effect.test {
            vm.onIntent(BookmarksIntent.ExportSelected)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is BookmarksEffect.ExportComplete)
            assertEquals(3, (effect as BookmarksEffect.ExportComplete).saved)
            assertEquals(2, effect.failed)
        }
    }

    /**
     * "Cannot save at all" and "saved nothing" are different answers and the user can only act on
     * one of them, so they must not collapse into the same effect.
     */
    @Test
    fun `an unsupported gallery emits ExportUnsupported rather than a zero count`() = runTest {
        seedOneBookmark()
        coEvery { bookmarkPageExporter.exportToGallery(any()) } returns ExportResult.GalleryUnsupported
        val vm = createViewModel()
        advanceUntilIdle()
        selectFirstBookmark(vm)

        vm.effect.test {
            vm.onIntent(BookmarksIntent.ExportSelected)
            advanceUntilIdle()
            assertEquals(BookmarksEffect.ExportUnsupported, awaitItem())
        }
    }

    /** #1133: images, not the text list this used to share. */
    @Test
    fun `share emits the prepared image uris`() = runTest {
        seedOneBookmark()
        coEvery { bookmarkPageExporter.prepareForShare(any()) } returns
            ShareResult(uris = listOf("content://x/1"), failed = 1)
        val vm = createViewModel()
        advanceUntilIdle()
        selectFirstBookmark(vm)

        vm.effect.test {
            vm.onIntent(BookmarksIntent.ShareSelected)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is BookmarksEffect.ShareImages)
            assertEquals(listOf("content://x/1"), (effect as BookmarksEffect.ShareImages).uris)
            assertEquals(1, effect.failed)
        }
    }

    /**
     * Nothing resolved, so there is nothing to hand the sharesheet. Launching it with an empty
     * list would open a chooser that shares an empty selection.
     */
    @Test
    fun `share with nothing resolvable falls back to a snackbar`() = runTest {
        seedOneBookmark()
        coEvery { bookmarkPageExporter.prepareForShare(any()) } returns
            ShareResult(uris = emptyList(), failed = 1)
        val vm = createViewModel()
        advanceUntilIdle()
        selectFirstBookmark(vm)

        vm.effect.test {
            vm.onIntent(BookmarksIntent.ShareSelected)
            advanceUntilIdle()
            assertTrue(awaitItem() is BookmarksEffect.ShowSnackbar)
        }
    }

    /** The selection clears once the work is done, not before it starts. */
    @Test
    fun `a completed export clears the selection`() = runTest {
        seedOneBookmark()
        coEvery { bookmarkPageExporter.exportToGallery(any()) } returns
            ExportResult.Completed(saved = 1, failed = 0)
        val vm = createViewModel()
        advanceUntilIdle()
        selectFirstBookmark(vm)
        assertTrue(vm.state.value.isSelectionMode)

        vm.onIntent(BookmarksIntent.ExportSelected)
        advanceUntilIdle()

        assertFalse(vm.state.value.isSelectionMode)
        assertFalse("progress must not stay stuck on", vm.state.value.isExporting)
    }

    /**
     * An exporter that throws must not leave the spinner running forever — the buttons are
     * disabled while [BookmarksState.isExporting] is true, so a leaked flag bricks the screen
     * until the user navigates away.
     */
    @Test
    fun `a failing exporter still clears the progress flag`() = runTest {
        seedOneBookmark()
        coEvery { bookmarkPageExporter.exportToGallery(any()) } throws IllegalStateException("boom")
        val vm = createViewModel()
        advanceUntilIdle()
        selectFirstBookmark(vm)

        vm.onIntent(BookmarksIntent.ExportSelected)
        advanceUntilIdle()

        assertFalse(vm.state.value.isExporting)
    }
}
