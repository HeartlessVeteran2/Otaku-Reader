package app.otakureader.data.repository

import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class StatisticsRepositoryImplTest {

    private lateinit var mangaDao: MangaDao
    private lateinit var readingHistoryDao: ReadingHistoryDao
    private lateinit var repository: StatisticsRepositoryImpl

    @Before
    fun setUp() {
        mangaDao = mockk()
        readingHistoryDao = mockk()
        repository = StatisticsRepositoryImpl(mangaDao, readingHistoryDao)

        // Default stubs so tests can override only what they care about
        every { mangaDao.getFavoriteMangaCount() } returns flowOf(0)
        every { readingHistoryDao.getTotalChaptersRead() } returns flowOf(0)
        every { readingHistoryDao.getTotalReadingTimeMs() } returns flowOf(0L)
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(emptyList())
        every { mangaDao.getFavoriteMangaGenres() } returns flowOf(emptyList())
    }

    // ── Overview totals ────────────────────────────────────────────────────────

    @Test
    fun getReadingStats_mapsTotalsFromDaos() = runTest {
        every { mangaDao.getFavoriteMangaCount() } returns flowOf(5)
        every { readingHistoryDao.getTotalChaptersRead() } returns flowOf(42)
        every { readingHistoryDao.getTotalReadingTimeMs() } returns flowOf(3_600_000L)

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(5, stats.totalMangaInLibrary)
            assertEquals(42, stats.totalChaptersRead)
            assertEquals(3_600_000L, stats.totalReadingTimeMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── computeStreaks ─────────────────────────────────────────────────────────

    @Test
    fun computeStreaks_emptyTimestamps_returnsZeroZero() = runTest {
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(emptyList())

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(0, stats.currentStreak)
            assertEquals(0, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeStreaks_singleDay_returnsOneOne() = runTest {
        // Use today's timestamp to ensure currentStreak = 1
        val todayMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(listOf(todayMs))

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(1, stats.currentStreak)
            assertEquals(1, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeStreaks_consecutiveDays_correctBestStreak() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // Build 5 consecutive days ending today
        val timestamps = (0L..4L).map { daysAgo ->
            today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(timestamps)

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(5, stats.currentStreak)
            assertEquals(5, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeStreaks_brokenStreak_bestStreakPreserved() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // 3 consecutive days 10+ days ago, gap, then today+yesterday
        val oldStreak = (10L..12L).map { daysAgo ->
            today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val recentStreak = listOf(0L, 1L).map { daysAgo ->
            today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(oldStreak + recentStreak)

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(2, stats.currentStreak)   // today + yesterday
            assertEquals(3, stats.bestStreak)      // old streak of 3
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeStreaks_duplicatesOnSameDay_countedOnce() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // Multiple reads on the same day should count as 1 streak day
        val todayEarly = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val todayLate = today.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(listOf(todayEarly, todayLate))

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(1, stats.currentStreak)
            assertEquals(1, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeStreaks_noReadingToday_currentStreakIsZero() = runTest {
        val zone = ZoneId.systemDefault()
        val yesterday = LocalDate.now(zone).minusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(listOf(yesterday))

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(0, stats.currentStreak)
            assertEquals(1, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── computeGenreDistribution ───────────────────────────────────────────────

    @Test
    fun computeGenreDistribution_emptyInput_returnsEmptyMap() = runTest {
        every { mangaDao.getFavoriteMangaGenres() } returns flowOf(emptyList())

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertTrue(stats.genreDistribution.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeGenreDistribution_commaSeparated_countsCorrectly() = runTest {
        every { mangaDao.getFavoriteMangaGenres() } returns flowOf(
            listOf("Action,Adventure", "Action,Romance")
        )

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(2, stats.genreDistribution["Action"])
            assertEquals(1, stats.genreDistribution["Adventure"])
            assertEquals(1, stats.genreDistribution["Romance"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeGenreDistribution_pipesSeparated_countsCorrectly() = runTest {
        every { mangaDao.getFavoriteMangaGenres() } returns flowOf(
            listOf("Action|||Adventure", "Action|||Isekai")
        )

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(2, stats.genreDistribution["Action"])
            assertEquals(1, stats.genreDistribution["Adventure"])
            assertEquals(1, stats.genreDistribution["Isekai"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeGenreDistribution_sortedByCountDescending() = runTest {
        every { mangaDao.getFavoriteMangaGenres() } returns flowOf(
            listOf("Comedy", "Action,Comedy", "Action,Comedy,Drama")
        )

        repository.getReadingStats().test {
            val stats = awaitItem()
            val keys = stats.genreDistribution.keys.toList()
            // Action:2, Comedy:3 → Comedy first
            assertEquals("Comedy", keys[0])
            assertEquals("Action", keys[1])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeGenreDistribution_capped_atMaxGenres() = runTest {
        // 11 distinct genres → only top 10 returned
        val genreString = (1..11).joinToString(",") { "Genre$it" }
        every { mangaDao.getFavoriteMangaGenres() } returns flowOf(listOf(genreString))

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(10, stats.genreDistribution.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── computeReadingActivity ─────────────────────────────────────────────────

    @Test
    fun computeReadingActivity_always90Entries() = runTest {
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(emptyList())

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(90, stats.readingActivityByDay.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeReadingActivity_zeroActivityForAllDaysWhenNoHistory() = runTest {
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(emptyList())

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertTrue(stats.readingActivityByDay.values.all { it == 0 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeReadingActivity_countsTimestampsOnCorrectDay() = runTest {
        val zone = ZoneId.systemDefault()
        val todayMs = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(
            listOf(todayMs, todayMs) // two reads today
        )

        repository.getReadingStats().test {
            val stats = awaitItem()
            val todayKey = LocalDate.now(zone).toString()
            assertEquals(2, stats.readingActivityByDay[todayKey])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeReadingActivity_timestampsOlderThan90Days_excluded() = runTest {
        val zone = ZoneId.systemDefault()
        // 91 days ago – outside the 90-day window
        val oldMs = LocalDate.now(zone).minusDays(91)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(listOf(oldMs))

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertTrue(stats.readingActivityByDay.values.all { it == 0 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun computeReadingActivity_firstDayIsExactly89DaysAgo() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val expectedFirst = today.minusDays(89).toString()
        val expectedLast = today.toString()

        every { readingHistoryDao.getAllReadTimestamps() } returns flowOf(emptyList())

        repository.getReadingStats().test {
            val stats = awaitItem()
            val keys = stats.readingActivityByDay.keys.toList()
            assertEquals(expectedFirst, keys.first())
            assertEquals(expectedLast, keys.last())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
