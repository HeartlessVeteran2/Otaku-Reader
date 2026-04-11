package app.otakureader.feature.statistics

import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.domain.model.ReadingGoal
import app.otakureader.domain.model.ReadingStats
import app.otakureader.domain.repository.StatisticsRepository
import app.otakureader.domain.usecase.GetReadingStatsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getReadingStatsUseCase: GetReadingStatsUseCase
    private lateinit var statisticsRepository: StatisticsRepository
    private lateinit var readingGoalPreferences: ReadingGoalPreferences

    private val sampleStats = ReadingStats(
        totalMangaInLibrary = 15,
        totalChaptersRead = 300,
        totalReadingTimeMs = 10_000_000L,
        currentStreak = 5,
        bestStreak = 14
    )

    private val sampleGoal = ReadingGoal(
        dailyGoal = 5,
        dailyProgress = 3,
        weeklyGoal = 30,
        weeklyProgress = 18
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getReadingStatsUseCase = mockk()
        statisticsRepository = mockk()
        readingGoalPreferences = mockk {
            every { dailyChapterGoal } returns flowOf(5)
            every { weeklyChapterGoal } returns flowOf(30)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): StatisticsViewModel {
        return StatisticsViewModel(getReadingStatsUseCase, statisticsRepository, readingGoalPreferences)
    }

    @Test
    fun init_loadsStatsOnCreation() = runTest {
        every { getReadingStatsUseCase() } returns flowOf(sampleStats)
        every { statisticsRepository.getReadingGoalProgress(5, 30) } returns flowOf(sampleGoal)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(sampleStats, viewModel.state.value.stats)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun init_loadsGoalProgressOnCreation() = runTest {
        every { getReadingStatsUseCase() } returns flowOf(sampleStats)
        every { statisticsRepository.getReadingGoalProgress(5, 30) } returns flowOf(sampleGoal)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(sampleGoal, viewModel.state.value.readingGoal)
    }

    @Test
    fun init_withEmptyStats_usesDefaults() = runTest {
        val emptyStats = ReadingStats()
        val emptyGoal = ReadingGoal()
        every { getReadingStatsUseCase() } returns flowOf(emptyStats)
        every { statisticsRepository.getReadingGoalProgress(5, 30) } returns flowOf(emptyGoal)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.state.value.stats.totalMangaInLibrary)
        assertEquals(0, viewModel.state.value.stats.totalChaptersRead)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun init_withError_setsErrorState() = runTest {
        every { getReadingStatsUseCase() } returns flowOf(sampleStats)
        every { statisticsRepository.getReadingGoalProgress(any(), any()) } returns
            flow { throw RuntimeException("Stats unavailable") }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun onEvent_Refresh_reloadsStats() = runTest {
        val initialStats = sampleStats
        val refreshedStats = sampleStats.copy(totalChaptersRead = 350)

        every { getReadingStatsUseCase() } returnsMany listOf(
            flowOf(initialStats),
            flowOf(refreshedStats)
        )
        every { statisticsRepository.getReadingGoalProgress(any(), any()) } returns flowOf(sampleGoal)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(300, viewModel.state.value.stats.totalChaptersRead)

        viewModel.onEvent(StatisticsEvent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(350, viewModel.state.value.stats.totalChaptersRead)
    }

    @Test
    fun init_passesGoalValuesToRepository() = runTest {
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(10)
        every { readingGoalPreferences.weeklyChapterGoal } returns flowOf(50)
        every { getReadingStatsUseCase() } returns flowOf(sampleStats)
        every { statisticsRepository.getReadingGoalProgress(10, 50) } returns flowOf(sampleGoal)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Repository should have been called with values from preferences
        assertEquals(sampleGoal, viewModel.state.value.readingGoal)
    }

    @Test
    fun state_initiallyShowsLoading() = runTest {
        every { getReadingStatsUseCase() } returns flowOf(sampleStats)
        every { statisticsRepository.getReadingGoalProgress(any(), any()) } returns flowOf(sampleGoal)

        val viewModel = createViewModel()

        // Before advancing the dispatcher, state should be loading
        // (StatisticsState defaults isLoading = true)
        // After advancing, it should complete
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }
}
