package app.otakureader.domain.usecase

import app.otakureader.domain.model.ReadingStats
import app.otakureader.domain.repository.StatisticsRepository
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetReadingStatsUseCaseTest {

    private lateinit var statisticsRepository: StatisticsRepository
    private lateinit var useCase: GetReadingStatsUseCase

    @Before
    fun setUp() {
        statisticsRepository = mockk()
        useCase = GetReadingStatsUseCase(statisticsRepository)
    }

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val stats = ReadingStats(
            totalMangaInLibrary = 10,
            totalChaptersRead = 250,
            totalReadingTimeMs = 5_000_000L,
            currentStreak = 3,
            bestStreak = 7
        )
        every { statisticsRepository.getReadingStats() } returns flowOf(stats)

        useCase().test {
            assertEquals(stats, awaitItem())
            awaitComplete()
        }

        verify(exactly = 1) { statisticsRepository.getReadingStats() }
    }

    @Test
    fun invoke_withEmptyStats_emitsDefault() = runTest {
        val emptyStats = ReadingStats()
        every { statisticsRepository.getReadingStats() } returns flowOf(emptyStats)

        useCase().test {
            val result = awaitItem()
            assertEquals(0, result.totalMangaInLibrary)
            assertEquals(0, result.totalChaptersRead)
            assertEquals(0L, result.totalReadingTimeMs)
            awaitComplete()
        }
    }

    @Test
    fun invoke_withGenreDistribution_emitsCorrectData() = runTest {
        val stats = ReadingStats(
            genreDistribution = mapOf("Action" to 5, "Romance" to 3),
            readingActivityByDay = mapOf("2024-01-01" to 10)
        )
        every { statisticsRepository.getReadingStats() } returns flowOf(stats)

        useCase().test {
            val result = awaitItem()
            assertEquals(5, result.genreDistribution["Action"])
            assertEquals(3, result.genreDistribution["Romance"])
            awaitComplete()
        }
    }
}
