package app.otakureader.feature.more

import app.cash.turbine.test
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadStatus
import app.otakureader.domain.model.ReadingGoal
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.ReaderSettingsRepository
import app.otakureader.domain.repository.StatisticsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers [MoreViewModel]'s live download-queue subtitle — Komikku's `MoreScreen` shows the
 * queue's paused/downloading pending count instead of a static description; this was missing
 * from Otaku's version entirely (always showed a static string) until this was ported.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val statisticsRepository: StatisticsRepository = mockk()
    private val readingGoalPreferences: ReadingGoalPreferences = mockk()
    private val readerSettingsRepository: ReaderSettingsRepository = mockk()
    private val generalPreferences: GeneralPreferences = mockk()
    private val downloadRepository: DownloadRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(0)
        every { readingGoalPreferences.weeklyChapterGoal } returns flowOf(0)
        every { statisticsRepository.getReadingGoalProgress(any(), any()) } returns flowOf(ReadingGoal())
        every { readerSettingsRepository.incognitoMode } returns flowOf(false)
        every { generalPreferences.downloadedOnly } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MoreViewModel(
        statisticsRepository = statisticsRepository,
        readingGoalPreferences = readingGoalPreferences,
        readerSettingsRepository = readerSettingsRepository,
        generalPreferences = generalPreferences,
        downloadRepository = downloadRepository,
    )

    private fun downloadItem(id: Long, status: DownloadStatus) = DownloadItem(
        id = id,
        mangaId = 1L,
        chapterId = id,
        mangaTitle = "Test Manga",
        chapterTitle = "Chapter $id",
        status = status,
    )

    @Test
    fun `download queue is Stopped when there are no downloads`() = runTest {
        every { downloadRepository.observeDownloads() } returns flowOf(emptyList())

        createViewModel().state.test {
            testDispatcher.scheduler.advanceUntilIdle()
            // No skipItems() here: the computed result is structurally equal to MoreState()'s
            // default (downloadQueueState = Stopped, all other fields also at their defaults),
            // and StateFlow only emits when the new value differs (by equals()) from the
            // current one — so this scenario produces exactly one emission, not two.
            val state = awaitItem()
            assertEquals(DownloadQueueDisplayState.Stopped, state.downloadQueueState)
        }
    }

    @Test
    fun `download queue is Downloading with the active count when a chapter is downloading`() = runTest {
        every { downloadRepository.observeDownloads() } returns flowOf(
            listOf(
                downloadItem(1L, DownloadStatus.DOWNLOADING),
                downloadItem(2L, DownloadStatus.QUEUED),
                downloadItem(3L, DownloadStatus.COMPLETED),
            )
        )

        createViewModel().state.test {
            skipItems(1) // initial MoreState() default before the combine() emits
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()
            assertTrue(state.downloadQueueState is DownloadQueueDisplayState.Downloading)
            assertEquals(2, (state.downloadQueueState as DownloadQueueDisplayState.Downloading).pending)
        }
    }

    @Test
    fun `download queue is Paused when active items exist but none are downloading`() = runTest {
        every { downloadRepository.observeDownloads() } returns flowOf(
            listOf(
                downloadItem(1L, DownloadStatus.PAUSED),
                downloadItem(2L, DownloadStatus.QUEUED),
            )
        )

        createViewModel().state.test {
            skipItems(1) // initial MoreState() default before the combine() emits
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()
            assertTrue(state.downloadQueueState is DownloadQueueDisplayState.Paused)
            assertEquals(2, (state.downloadQueueState as DownloadQueueDisplayState.Paused).pending)
        }
    }
}
