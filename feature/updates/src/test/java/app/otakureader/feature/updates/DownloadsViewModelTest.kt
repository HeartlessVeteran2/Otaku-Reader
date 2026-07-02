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

    // Queued in id order 1,2,3 — sort tests verify reorderDownload is called in the new order.
    private val queueItems = listOf(
        DownloadItem(id = 1L, mangaId = 1L, chapterId = 1L, mangaTitle = "Naruto", chapterTitle = "Ch 1", status = DownloadStatus.QUEUED),
        DownloadItem(id = 2L, mangaId = 1L, chapterId = 2L, mangaTitle = "Naruto", chapterTitle = "Ch 2", status = DownloadStatus.QUEUED),
        DownloadItem(id = 3L, mangaId = 1L, chapterId = 3L, mangaTitle = "Naruto", chapterTitle = "Ch 3", status = DownloadStatus.QUEUED),
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
}
