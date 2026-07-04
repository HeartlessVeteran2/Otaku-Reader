package app.otakureader.data.repository

import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.dao.TrackEntryDao
import app.otakureader.core.database.entity.ReadingHistoryEntity
import app.otakureader.domain.repository.StatisticsRepository
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatisticsRepositoryImplTest {

    private lateinit var readingHistoryDao: ReadingHistoryDao
    private lateinit var mangaDao: MangaDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var trackEntryDao: TrackEntryDao
    private lateinit var repository: StatisticsRepository

    private fun makeEntry(readAt: Long, durationMs: Long = 0L) =
        ReadingHistoryEntity(chapterId = 1L, readAt = readAt, readDurationMs = durationMs)

    private fun dayMs(daysAgo: Long): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    @Before
    fun setUp() {
        readingHistoryDao = mockk()
        mangaDao = mockk()
        chapterDao = mockk()
        trackEntryDao = mockk()
        repository = StatisticsRepositoryImpl(readingHistoryDao, mangaDao, chapterDao, trackEntryDao)

        // Default stub
        every { readingHistoryDao.observeHistory() } returns flowOf(emptyList())
        every { mangaDao.countFavorites() } returns flowOf(0)
        every { mangaDao.getFavoriteMangaGenres() } returns flowOf(emptyList())
        every { mangaDao.getCompletedMangaCount() } returns flowOf(0)
        every { chapterDao.getTotalChapterCountForLibrary() } returns flowOf(0)
        every { trackEntryDao.getTrackedMangaCount() } returns flowOf(0)
        every { trackEntryDao.getMeanScore() } returns flowOf(null)
        every { trackEntryDao.getTrackerServiceCount() } returns flowOf(0)
    }

    // ── getReadingStats: totals ────────────────────────────────────────────────

    @Test
    fun `getReadingStats empty history returns zero totals`() = runTest {
        every { readingHistoryDao.observeHistory() } returns flowOf(emptyList())

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(0, stats.totalChaptersRead)
            assertEquals(0L, stats.totalReadingTimeMs)
            assertEquals(0, stats.currentStreak)
            assertEquals(0, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingStats counts chapters and sums reading time`() = runTest {
        val entries = listOf(
            makeEntry(dayMs(0), durationMs = 1_000L),
            makeEntry(dayMs(0), durationMs = 2_000L),
            makeEntry(dayMs(1), durationMs = 500L),
        )
        every { readingHistoryDao.observeHistory() } returns flowOf(entries)

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(3, stats.totalChaptersRead)
            assertEquals(3_500L, stats.totalReadingTimeMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getReadingStats: streaks ───────────────────────────────────────────────

    @Test
    fun `getReadingStats single day today returns streak of 1`() = runTest {
        every { readingHistoryDao.observeHistory() } returns flowOf(listOf(makeEntry(dayMs(0))))

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(1, stats.currentStreak)
            assertEquals(1, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingStats consecutive days returns correct streak`() = runTest {
        val entries = (0L..4L).map { makeEntry(dayMs(it)) }
        every { readingHistoryDao.observeHistory() } returns flowOf(entries)

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(5, stats.currentStreak)
            assertEquals(5, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingStats broken streak preserves best streak`() = runTest {
        // Old streak: 3 days ending 10 days ago; recent: 2 days (today + yesterday)
        val oldStreak = (10L..12L).map { makeEntry(dayMs(it)) }
        val recent = (0L..1L).map { makeEntry(dayMs(it)) }
        every { readingHistoryDao.observeHistory() } returns flowOf(oldStreak + recent)

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(2, stats.currentStreak)
            assertEquals(3, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingStats no reading today or yesterday returns currentStreak zero`() = runTest {
        // Read 2 days ago but not today or yesterday — streak is broken
        every { readingHistoryDao.observeHistory() } returns flowOf(listOf(makeEntry(dayMs(2))))

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(0, stats.currentStreak)
            assertEquals(1, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingStats duplicates on same day counted once in streak`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val earlyMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val lateMs = today.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        every { readingHistoryDao.observeHistory() } returns flowOf(
            listOf(makeEntry(earlyMs), makeEntry(lateMs))
        )

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(1, stats.currentStreak)
            assertEquals(1, stats.bestStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getReadingStats: activity map ─────────────────────────────────────────

    @Test
    fun `getReadingStats activity map has 30 entries`() = runTest {
        every { readingHistoryDao.observeHistory() } returns flowOf(emptyList())

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(30, stats.readingActivityByDay.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingStats activity map all zeros when no history`() = runTest {
        every { readingHistoryDao.observeHistory() } returns flowOf(emptyList())

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertTrue(stats.readingActivityByDay.values.all { it == 0 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingStats activity map counts today correctly`() = runTest {
        every { readingHistoryDao.observeHistory() } returns flowOf(
            listOf(makeEntry(dayMs(0)), makeEntry(dayMs(0)))
        )

        repository.getReadingStats().test {
            val stats = awaitItem()
            val todayKey = LocalDate.now(ZoneId.systemDefault()).toString()
            assertEquals(2, stats.readingActivityByDay[todayKey])
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getReadingStats: genre distribution ────────────────────────────────────

    @Test
    fun `getReadingStats builds genre distribution from favorite manga genres`() = runTest {
        every { mangaDao.countFavorites() } returns flowOf(4)
        every { mangaDao.getFavoriteMangaGenres() } returns flowOf(
            listOf(
                "Action, Adventure",
                "Action; Drama",
                "Comedy | Action",
                "  ",
            )
        )

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(4, stats.totalMangaInLibrary)
            assertEquals(
                mapOf(
                    "Action" to 3,
                    "Adventure" to 1,
                    "Comedy" to 1,
                    "Drama" to 1,
                ),
                stats.genreDistribution,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getReadingStats: library detail ────────────────────────────────────────

    @Test
    fun `getReadingStats surfaces completed manga count and total chapter count`() = runTest {
        every { mangaDao.getCompletedMangaCount() } returns flowOf(7)
        every { chapterDao.getTotalChapterCountForLibrary() } returns flowOf(142)

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(7, stats.completedMangaCount)
            assertEquals(142, stats.totalChapterCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getReadingStats: tracker stats ─────────────────────────────────────────

    @Test
    fun `getReadingStats surfaces tracker aggregates`() = runTest {
        every { trackEntryDao.getTrackedMangaCount() } returns flowOf(12)
        every { trackEntryDao.getMeanScore() } returns flowOf(7.5f)
        every { trackEntryDao.getTrackerServiceCount() } returns flowOf(3)

        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(12, stats.trackedMangaCount)
            assertEquals(7.5f, stats.meanTrackerScore)
            assertEquals(3, stats.trackerServiceCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingStats with no tracked manga returns null mean score`() = runTest {
        repository.getReadingStats().test {
            val stats = awaitItem()
            assertEquals(0, stats.trackedMangaCount)
            assertEquals(null, stats.meanTrackerScore)
            assertEquals(0, stats.trackerServiceCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getReadingGoalProgress ─────────────────────────────────────────────────

    @Test
    fun `getReadingGoalProgress with no history returns zero progress`() = runTest {
        every { readingHistoryDao.observeHistory() } returns flowOf(emptyList())

        repository.getReadingGoalProgress(dailyGoal = 5, weeklyGoal = 20).test {
            val goal = awaitItem()
            assertEquals(5, goal.dailyGoal)
            assertEquals(20, goal.weeklyGoal)
            assertEquals(0, goal.dailyProgress)
            assertEquals(0, goal.weeklyProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingGoalProgress counts today chapters correctly`() = runTest {
        every { readingHistoryDao.observeHistory() } returns flowOf(
            listOf(makeEntry(dayMs(0)), makeEntry(dayMs(0)))
        )

        repository.getReadingGoalProgress(dailyGoal = 5, weeklyGoal = 20).test {
            val goal = awaitItem()
            assertEquals(2, goal.dailyProgress)
            assertEquals(2, goal.weeklyProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingGoalProgress weekly includes earlier days in same ISO week`() = runTest {
        // weekStart = this Monday; only valid when today is not Monday (otherwise no prior days in week).
        val daysFromMonday = java.time.LocalDate.now().dayOfWeek.value.toLong() - 1
        if (daysFromMonday == 0L) return@runTest  // Monday: no earlier days in this week, skip
        every { readingHistoryDao.observeHistory() } returns flowOf(
            listOf(makeEntry(dayMs(0)), makeEntry(dayMs(0)), makeEntry(dayMs(1)))
        )

        repository.getReadingGoalProgress(dailyGoal = 5, weeklyGoal = 20).test {
            val goal = awaitItem()
            assertEquals(2, goal.dailyProgress)
            assertEquals(3, goal.weeklyProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReadingGoalProgress zero goals returns zero goals`() = runTest {
        every { readingHistoryDao.observeHistory() } returns flowOf(emptyList())

        repository.getReadingGoalProgress(dailyGoal = 0, weeklyGoal = 0).test {
            val goal = awaitItem()
            assertEquals(0, goal.dailyGoal)
            assertEquals(0, goal.weeklyGoal)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
