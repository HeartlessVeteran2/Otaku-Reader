package app.otakureader.data.bookmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.domain.model.BookmarkPageRef
import app.otakureader.domain.model.ExportResult
import app.otakureader.domain.loader.PageLoader
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceChapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers [BookmarkPageExporterImpl]'s page resolution (#1132, #1133).
 *
 * These assert the part that is this class's own work — deciding *where* a bookmarked page comes
 * from, and how often the source is asked. What happens after that is `MediaStore` and
 * `FileProvider`, which belong to the platform and are not usefully faked here; the tests
 * therefore verify calls and counts rather than the saved-page tally.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BookmarkPageExporterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private val mangaRepository: MangaRepository = mockk(relaxed = true)
    private val chapterRepository: ChapterRepository = mockk(relaxed = true)
    private val sourceRepository: SourceRepository = mockk(relaxed = true)
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)
    private val pageLoader: PageLoader = mockk(relaxed = true)

    private val sourceKey = 7L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        coEvery { downloadRepository.downloadFolderNameFor(sourceKey) } returns "MangaDex"
        coEvery { mangaRepository.getMangaById(10L) } returns
            Manga(id = 10L, sourceId = sourceKey, url = "/m/10", title = "Vinland Saga")
        coEvery { chapterRepository.getChapterById(100L) } returns
            Chapter(id = 100L, mangaId = 10L, url = "/c/1", name = "Chapter 1")
        // No local copy unless a test says otherwise.
        every { pageLoader.resolveUrl(any(), any(), any(), any(), any()) } returns ""
        val source: MangaSource = mockk(relaxed = true) { every { id } returns "en.mangadex" }
        every { sourceRepository.getSources() } returns kotlinx.coroutines.flow.flowOf(listOf(source))
        coEvery { sourceRepository.getSourceByKey(sourceKey) } returns source
    }

    private fun exporter() = BookmarkPageExporterImpl(
        context = context,
        mangaRepository = mangaRepository,
        chapterRepository = chapterRepository,
        sourceRepository = sourceRepository,
        downloadRepository = downloadRepository,
        pageLoader = pageLoader,
        pageImageClient = OkHttpClient(),
    )

    private fun ref(pageIndex: Int, chapterId: Long = 100L) = BookmarkPageRef(
        mangaId = 10L,
        chapterId = chapterId,
        pageIndex = pageIndex,
        mangaTitle = "Vinland Saga",
        chapterName = "Chapter 1",
    )

    /**
     * A downloaded page is read from disk and the source is never asked.
     *
     * This is the leg that has to stay cheap: a user exporting panels from a chapter they already
     * downloaded should not cause a page-list request, let alone an image fetch.
     */
    @Test
    fun `a downloaded page is read from disk without touching the source`() = runTest {
        val file = temporaryFolder.newFile("0.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        every { pageLoader.resolveUrl("", "MangaDex", "Vinland Saga", "Chapter 1", 0) } returns
            "file://${file.absolutePath}"

        exporter().exportToGallery(listOf(ref(0)))

        coVerify(exactly = 0) { sourceRepository.getPageList(any(), any()) }
    }

    /**
     * The page list is fetched once per chapter, not once per bookmark.
     *
     * Selecting a run of panels from one chapter is the normal way this feature gets used, so a
     * per-bookmark fetch would multiply source requests by the size of the selection. Three
     * bookmarks in one chapter must still produce one request.
     */
    @Test
    fun `pages in the same chapter share a single page-list fetch`() = runTest {
        coEvery { sourceRepository.getPageList(any(), any()) } returns Result.success(emptyList())

        exporter().exportToGallery(listOf(ref(0), ref(1), ref(2)))

        coVerify(exactly = 1) { sourceRepository.getPageList(any(), any<SourceChapter>()) }
    }

    /**
     * Below API 29 a `MediaStore` write to external storage needs `WRITE_EXTERNAL_STORAGE`, which
     * this app does not request — so nothing is attempted, and the result must say that rather
     * than report an export that saved nothing. The two mean different things to a user: one is
     * "your device can't", the other is "your pages couldn't be found".
     */
    @Test
    @Config(sdk = [28])
    fun `an old device reports the gallery as unsupported rather than an empty export`() = runTest {
        val result = exporter().exportToGallery(listOf(ref(0)))

        assertEquals(ExportResult.GalleryUnsupported, result)
        // And it short-circuits: nothing is resolved for an export that cannot happen.
        coVerify(exactly = 0) { sourceRepository.getPageList(any(), any()) }
    }

    /**
     * A bookmark whose manga row is gone counts as a failure and stops there. It must not be
     * silently dropped from the tally, and it must not reach the source with a null manga.
     */
    @Test
    fun `a bookmark with no manga row is counted as failed`() = runTest {
        coEvery { mangaRepository.getMangaById(10L) } returns null

        val result = exporter().exportToGallery(listOf(ref(0)))

        assertTrue(result is ExportResult.Completed)
        assertEquals(0, (result as ExportResult.Completed).saved)
        assertEquals(1, result.failed)
        coVerify(exactly = 0) { sourceRepository.getPageList(any(), any()) }
    }

    /**
     * An uninstalled extension leaves the manga in the library with no source to ask. That is a
     * normal outcome, not a crash, and every page under it fails rather than aborting the batch.
     */
    @Test
    fun `an unresolvable source fails its pages without throwing`() = runTest {
        coEvery { sourceRepository.getSourceByKey(sourceKey) } returns null
        every { sourceRepository.getSources() } returns kotlinx.coroutines.flow.flowOf(emptyList())

        val result = exporter().exportToGallery(listOf(ref(0), ref(1)))

        assertEquals(2, (result as ExportResult.Completed).failed)
    }

    /** A page index past the end of the chapter's page list is a failure, not an exception. */
    @Test
    fun `a page index beyond the chapter's page list fails that page only`() = runTest {
        val file = temporaryFolder.newFile("local.jpg").apply { writeBytes(byteArrayOf(9)) }
        every { pageLoader.resolveUrl("", "MangaDex", "Vinland Saga", "Chapter 1", 0) } returns
            "file://${file.absolutePath}"
        coEvery { sourceRepository.getPageList(any(), any()) } returns Result.success(emptyList())

        val result = exporter().exportToGallery(listOf(ref(0), ref(99)))

        assertTrue("the out-of-range page must fail", (result as ExportResult.Completed).failed >= 1)
    }

    /**
     * `resolveUrl` returns its `pageUrl` argument unchanged when nothing local exists, and the
     * exporter passes an empty one — so an empty answer must be read as "not downloaded" and not
     * turned into a `File("")`, which would resolve to the working directory.
     */
    @Test
    fun `an empty resolveUrl answer is treated as no local copy`() = runTest {
        coEvery { sourceRepository.getPageList(any(), any()) } returns Result.success(emptyList())

        exporter().exportToGallery(listOf(ref(0)))

        // It fell through to the source rather than believing it had a local file.
        coVerify(exactly = 1) { sourceRepository.getPageList(any(), any<SourceChapter>()) }
    }

    /**
     * Files from an earlier share are cleared before the next one writes (#1133).
     *
     * They cannot be deleted *after* a share: the sharesheet reports neither which app was chosen
     * nor when it finished reading the URIs, so deleting on completion would race a receiver that
     * has not opened the stream yet. Clearing on the next share is what bounds the directory, and
     * this asserts the leftover is actually gone rather than merely joined by new files.
     */
    @Test
    fun `an earlier share's temporary files are cleared before the next one`() = runTest {
        val shareDir = File(context.cacheDir, "shared_bookmarks").apply { mkdirs() }
        val leftover = File(shareDir, "stale_page.jpg").apply { writeText("old") }
        val file = temporaryFolder.newFile("page.jpg").apply { writeBytes(byteArrayOf(4, 2)) }
        every { pageLoader.resolveUrl("", "MangaDex", "Vinland Saga", "Chapter 1", 0) } returns
            "file://${file.absolutePath}"

        exporter().prepareForShare(listOf(ref(0)))

        assertFalse("the previous share's file must be gone", leftover.exists())
        // The resulting URI count is deliberately not asserted: `FileProvider` is declared in the
        // app module's manifest, so `getUriForFile` cannot resolve a root from this module's test
        // manifest. The purge is what this test is for, and it runs before that step.
    }
}
