package app.otakureader.data.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.otakureader.core.preferences.DeleteAfterReadMode
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the durable exit-time counterpart of [app.otakureader.feature.reader.viewmodel.delegate
 * .ReaderDeleteAfterReadDelegate] — this worker must apply the same delete-after-reading decision
 * (global toggle + per-manga override precedence) when the reader is closed before the live
 * in-session debounce timer fires, or the process dies before it completes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordReadingHistoryWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var goalCompletionNotifier: GoalCompletionNotifier
    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var mangaRepository: MangaRepository
    private lateinit var sourceRepository: SourceRepository

    private val mangaId = 1L
    private val chapterId = 10L
    private val testManga = Manga(id = mangaId, sourceId = 99L, url = "https://example.com/manga", title = "Test Manga")
    private val testChapter = Chapter(
        id = chapterId,
        mangaId = mangaId,
        url = "https://example.com/chapter-10",
        name = "Chapter 10",
        chapterNumber = 10f,
    )

    private fun buildWorker(inputData: androidx.work.Data): RecordReadingHistoryWorker {
        every { workerParams.inputData } returns inputData
        return RecordReadingHistoryWorker(
            context,
            workerParams,
            chapterRepository,
            goalCompletionNotifier,
            downloadPreferences,
            downloadRepository,
            mangaRepository,
            sourceRepository,
        )
    }

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        chapterRepository = mockk()
        goalCompletionNotifier = mockk(relaxed = true)
        downloadPreferences = mockk()
        downloadRepository = mockk()
        mangaRepository = mockk()
        sourceRepository = mockk(relaxed = true)

        coEvery { chapterRepository.recordHistory(any(), any(), any()) } just runs
        coEvery { chapterRepository.updateChapterProgress(any<Long>(), any<Boolean>(), any<Int>()) } just runs
        coEvery { mangaRepository.getMangaById(mangaId) } returns testManga
        coEvery { chapterRepository.getChapterById(chapterId) } returns testChapter
        coEvery { downloadRepository.isChapterDownloaded(any(), any(), any()) } returns true
        coEvery { downloadRepository.deleteChapterDownload(any(), any(), any(), any()) } just runs
        // No installed source for this fixture id — resolveDownloadFolderName falls back to the
        // numeric sourceId string.
        coEvery { sourceRepository.getSource(any()) } returns null
        // Default: immediate delete-after-read (slots = 0).
        every { downloadPreferences.removeAfterReadSlots } returns flowOf(0)
    }

    private fun readCompletedInputData(): androidx.work.Data = workDataOf(
        RecordReadingHistoryWorker.KEY_CHAPTER_ID to chapterId,
        RecordReadingHistoryWorker.KEY_MANGA_ID to mangaId,
        RecordReadingHistoryWorker.KEY_READ_AT to 1_000L,
        RecordReadingHistoryWorker.KEY_DURATION_MS to 5_000L,
        RecordReadingHistoryWorker.KEY_IS_INCOGNITO to false,
        RecordReadingHistoryWorker.KEY_LAST_PAGE_READ to 9,
        RecordReadingHistoryWorker.KEY_IS_READ to true,
    )

    @Test
    fun `deletes the download when delete-after-reading is globally enabled`() = runTest {
        every { downloadPreferences.deleteAfterReading } returns flowOf(true)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())

        val result = buildWorker(readCompletedInputData()).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            downloadRepository.deleteChapterDownload(chapterId, "99", "Test Manga", "Chapter 10")
        }
    }

    @Test
    fun `does not delete when a per-manga override disables it despite the global setting`() = runTest {
        every { downloadPreferences.deleteAfterReading } returns flowOf(true)
        every { downloadPreferences.perMangaOverrides } returns flowOf(mapOf(mangaId to DeleteAfterReadMode.DISABLED))

        buildWorker(readCompletedInputData()).doWork()

        coVerify(exactly = 0) { downloadRepository.deleteChapterDownload(any(), any(), any(), any()) }
    }

    @Test
    fun `deletes when a per-manga override enables it despite the global setting`() = runTest {
        every { downloadPreferences.deleteAfterReading } returns flowOf(false)
        every { downloadPreferences.perMangaOverrides } returns flowOf(mapOf(mangaId to DeleteAfterReadMode.ENABLED))

        buildWorker(readCompletedInputData()).doWork()

        coVerify(exactly = 1) {
            downloadRepository.deleteChapterDownload(chapterId, "99", "Test Manga", "Chapter 10")
        }
    }

    @Test
    fun `with slots deletes the chapter N back in reading order instead of the just-read one`() = runTest {
        every { downloadPreferences.deleteAfterReading } returns flowOf(true)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
        every { downloadPreferences.removeAfterReadSlots } returns flowOf(2)
        val earlier = testChapter.copy(id = 8L, name = "Chapter 8", chapterNumber = 8f)
        val middle = testChapter.copy(id = 9L, name = "Chapter 9", chapterNumber = 9f)
        coEvery { chapterRepository.getChaptersByMangaIdSync(mangaId) } returns
            listOf(testChapter, earlier, middle)

        buildWorker(readCompletedInputData()).doWork()

        // Read ch10 with keep-last-2 → ch8 is deleted, ch9 and ch10 stay.
        coVerify(exactly = 1) {
            downloadRepository.deleteChapterDownload(8L, "99", "Test Manga", "Chapter 8")
        }
    }

    @Test
    fun `with slots does not delete anything when no chapter is far enough back`() = runTest {
        every { downloadPreferences.deleteAfterReading } returns flowOf(true)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
        every { downloadPreferences.removeAfterReadSlots } returns flowOf(2)
        coEvery { chapterRepository.getChaptersByMangaIdSync(mangaId) } returns
            listOf(testChapter, testChapter.copy(id = 9L, name = "Chapter 9", chapterNumber = 9f))

        buildWorker(readCompletedInputData()).doWork()

        coVerify(exactly = 0) { downloadRepository.deleteChapterDownload(any(), any(), any(), any()) }
    }

    @Test
    fun `does not delete when the chapter is not marked read`() = runTest {
        every { downloadPreferences.deleteAfterReading } returns flowOf(true)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
        val inputData = workDataOf(
            RecordReadingHistoryWorker.KEY_CHAPTER_ID to chapterId,
            RecordReadingHistoryWorker.KEY_MANGA_ID to mangaId,
            RecordReadingHistoryWorker.KEY_READ_AT to 1_000L,
            RecordReadingHistoryWorker.KEY_DURATION_MS to 5_000L,
            RecordReadingHistoryWorker.KEY_IS_INCOGNITO to false,
            RecordReadingHistoryWorker.KEY_LAST_PAGE_READ to 3,
            RecordReadingHistoryWorker.KEY_IS_READ to false,
        )

        buildWorker(inputData).doWork()

        coVerify(exactly = 0) { downloadRepository.deleteChapterDownload(any(), any(), any(), any()) }
    }

    @Test
    fun `does not delete a chapter that has no downloaded pages`() = runTest {
        every { downloadPreferences.deleteAfterReading } returns flowOf(true)
        every { downloadPreferences.perMangaOverrides } returns flowOf(emptyMap())
        coEvery { downloadRepository.isChapterDownloaded(any(), any(), any()) } returns false

        buildWorker(readCompletedInputData()).doWork()

        coVerify(exactly = 0) { downloadRepository.deleteChapterDownload(any(), any(), any(), any()) }
    }
}
