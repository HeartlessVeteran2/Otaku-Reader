package app.otakureader.feature.updates

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadStatus
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import io.mockk.coEvery
import io.mockk.coVerifyOrder
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var chapterRepository: ChapterRepository

    // DownloadItem.id is deliberately different from chapterId (offset by 100) so a regression
    // to sorting/reordering by item.id instead of item.chapterId would fail these tests.
    private val queueItems = listOf(
        DownloadItem(id = 101L, mangaId = 1L, chapterId = 1L, mangaTitle = "Naruto", chapterTitle = "Ch 1", status = DownloadStatus.QUEUED),
        DownloadItem(id = 102L, mangaId = 1L, chapterId = 2L, mangaTitle = "Naruto", chapterTitle = "Ch 2", status = DownloadStatus.QUEUED),
        DownloadItem(id = 103L, mangaId = 1L, chapterId = 3L, mangaTitle = "Naruto", chapterTitle = "Ch 3", status = DownloadStatus.QUEUED),
    )

    private val chapters = mapOf(
        1L to Chapter(id = 1L, mangaId = 1L, url = "/c/1", name = "Ch 1", chapterNumber = 3f, dateUpload = 300L),
        2L to Chapter(id = 2L, mangaId = 1L, url = "/c/2", name = "Ch 2", chapterNumber = 1f, dateUpload = 100L),
        3L to Chapter(id = 3L, mangaId = 1L, url = "/c/3", name = "Ch 3", chapterNumber = 2f, dateUpload = 200L),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        downloadRepository = mockk(relaxed = true)
        chapterRepository = mockk()
        every { downloadRepository.observeDownloads() } returns flowOf(queueItems)
        chapters.forEach { (id, chapter) ->
            coEvery { chapterRepository.getChapterById(id) } returns chapter
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DownloadsViewModel(downloadRepository, chapterRepository)

    @Test
    fun onEvent_SortByChapterNumber_ascending_reordersByChapterNumberLowToHigh() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DownloadsEvent.SortByChapterNumber(ascending = true))
        testDispatcher.scheduler.advanceUntilIdle()

        // Chapter numbers: id=2 -> 1f, id=3 -> 2f, id=1 -> 3f
        coVerifyOrder {
            downloadRepository.reorderDownload(2L, 0)
            downloadRepository.reorderDownload(3L, 1)
            downloadRepository.reorderDownload(1L, 2)
        }
    }

    @Test
    fun onEvent_SortByUploadDate_newestFirst_reordersByDateUploadHighToLow() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DownloadsEvent.SortByUploadDate(newestFirst = true))
        testDispatcher.scheduler.advanceUntilIdle()

        // dateUpload: id=1 -> 300L, id=3 -> 200L, id=2 -> 100L
        coVerifyOrder {
            downloadRepository.reorderDownload(1L, 0)
            downloadRepository.reorderDownload(3L, 1)
            downloadRepository.reorderDownload(2L, 2)
        }
    }

    @Test
    fun onEvent_SortByChapterNumber_chapterLookupMissing_appendsItInsteadOfDropping() = runTest {
        // Chapter 2 was deleted from the DB while still queued for download.
        coEvery { chapterRepository.getChapterById(2L) } returns null

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(DownloadsEvent.SortByChapterNumber(ascending = true))
        testDispatcher.scheduler.advanceUntilIdle()

        // id=2's chapter is missing, so it's appended after the sortable items (id=3 -> 2f, id=1 -> 3f)
        // rather than silently dropped from the reorder.
        coVerifyOrder {
            downloadRepository.reorderDownload(3L, 0)
            downloadRepository.reorderDownload(1L, 1)
            downloadRepository.reorderDownload(2L, 2)
        }
    }
}
