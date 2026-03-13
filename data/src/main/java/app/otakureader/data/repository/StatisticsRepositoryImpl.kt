package app.otakureader.data.repository

import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.domain.model.ReadingGoal
import app.otakureader.domain.model.ReadingStats
import app.otakureader.domain.repository.StatisticsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatisticsRepositoryImpl @Inject constructor(
    private val mangaDao: MangaDao,
    private val readingHistoryDao: ReadingHistoryDao
) : StatisticsRepository {

    override fun getReadingStats(): Flow<ReadingStats> = combine(
        mangaDao.getFavoriteMangaCount(),
        readingHistoryDao.getTotalChaptersRead(),
        readingHistoryDao.getTotalReadingTimeMs(),
        readingHistoryDao.getAllReadTimestamps(),
        mangaDao.getFavoriteMangaGenres()
    ) { mangaCount, chaptersRead, readingTimeMs, timestamps, genreStrings ->
        val streaks = computeStreaks(timestamps)
        val genreDistribution = computeGenreDistribution(genreStrings)
        val readingActivity = computeReadingActivity(timestamps)
        ReadingStats(
            totalMangaInLibrary = mangaCount,
            totalChaptersRead = chaptersRead,
            totalReadingTimeMs = readingTimeMs,
            currentStreak = streaks.first,
            bestStreak = streaks.second,
            genreDistribution = genreDistribution,
            readingActivityByDay = readingActivity
        )
    }

    /** Returns (currentStreak, bestStreak) in days. */
    private fun computeStreaks(timestamps: List<Long>): Pair<Int, Int> {
        if (timestamps.isEmpty()) return Pair(0, 0)

        val zone = ZoneId.systemDefault()
        val distinctDays = timestamps
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .distinct()
            .sorted()

        var bestStreak = 1
        var currentRun = 1
        for (i in 1 until distinctDays.size) {
            if (distinctDays[i].minusDays(1) == distinctDays[i - 1]) {
                currentRun++
                if (currentRun > bestStreak) bestStreak = currentRun
            } else {
                currentRun = 1
            }
        }

        // Current streak: count back from today
        val today = java.time.LocalDate.now(zone)
        var currentStreak = 0
        var day = today
        val daySet = distinctDays.toSet()
        while (daySet.contains(day)) {
            currentStreak++
            day = day.minusDays(1)
        }

        return Pair(currentStreak, bestStreak)
    }

    /** Parses genre strings (comma- or "|||"-separated) and counts occurrences, sorted descending. */
    private fun computeGenreDistribution(genreStrings: List<String>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (raw in genreStrings) {
            val genres = raw.split(",", "|||").map { it.trim() }.filter { it.isNotBlank() }
            for (genre in genres) {
                counts[genre] = (counts[genre] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(MAX_GENRES)
            .associate { it.key to it.value }
    }

    /** Builds a map of ISO date string → activity count for the last ACTIVITY_DAYS days. */
    private fun computeReadingActivity(timestamps: List<Long>): Map<String, Int> {
        val zone = ZoneId.systemDefault()
        val counts = TreeMap<String, Int>()

        // Define an inclusive date range covering exactly ACTIVITY_DAYS days ending today.
        val endDate = java.time.LocalDate.now(zone)
        val startDate = endDate.minusDays(ACTIVITY_DAYS.toLong() - 1L)

        // Initialize all days in the range with zero activity so the UI gets a fixed grid.
        var date = startDate
        while (!date.isAfter(endDate)) {
            val key = DateTimeFormatter.ISO_LOCAL_DATE.format(date)
            counts[key] = 0
            date = date.plusDays(1)
        }

        // Increment counts for timestamps that fall within the date range.
        for (ts in timestamps) {
            val tsDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
            if (!tsDate.isBefore(startDate) && !tsDate.isAfter(endDate)) {
                val key = DateTimeFormatter.ISO_LOCAL_DATE.format(tsDate)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts
    }

    /**
     * Returns a Flow that emits the start-of-day and start-of-week timestamps (in millis)
     * for the current date in the given time zone, and updates them whenever the local
     * day changes (i.e., at midnight).
     */
    private fun createDailyWeeklyAnchorFlow(zone: ZoneId): Flow<Pair<Long, Long>> = flow {
        // Emit initial anchors immediately.
        emit(computeDailyWeeklyAnchors(zone))

        while (true) {
            val nowInstant = Instant.now()
            val nowZoned = nowInstant.atZone(zone)
            val nextMidnightZoned = nowZoned.toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
            val nextMidnightMillis = nextMidnightZoned.toInstant().toEpochMilli()
            val nowMillis = nowInstant.toEpochMilli()
            val delayMillis = nextMidnightMillis - nowMillis

            if (delayMillis > 0) {
                delay(delayMillis)
            }

            emit(computeDailyWeeklyAnchors(zone))
        }
    }.distinctUntilChanged()

    private fun computeDailyWeeklyAnchors(zone: ZoneId): Pair<Long, Long> {
        val today = LocalDate.now(zone)
        val startOfDayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfWeekMs = today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return startOfDayMs to startOfWeekMs
    }

    override fun getReadingGoalProgress(dailyGoal: Int, weeklyGoal: Int): Flow<ReadingGoal> {
        val zone = ZoneId.systemDefault()

        return createDailyWeeklyAnchorFlow(zone).flatMapLatest { (startOfDayMs, startOfWeekMs) ->
            combine(
                readingHistoryDao.getChaptersReadSince(startOfDayMs),
                readingHistoryDao.getChaptersReadSince(startOfWeekMs)
            ) { dailyProgress, weeklyProgress ->
                ReadingGoal(
                    dailyGoal = dailyGoal,
                    dailyProgress = dailyProgress,
                    weeklyGoal = weeklyGoal,
                    weeklyProgress = weeklyProgress
                )
            }
        }
    }

    companion object {
        private const val MAX_GENRES = 10
        private const val ACTIVITY_DAYS = 90
    }
}
