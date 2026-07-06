package app.otakureader.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.components.MonoLabel
import app.otakureader.core.ui.theme.LocalOtakuColors
import app.otakureader.domain.model.Achievement
import app.otakureader.domain.model.ReadingGoal
import app.otakureader.domain.model.ReadingStats
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showShareSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.statistics_back)
                        )
                    }
                },
                actions = {
                    if (!state.isLoading && state.error == null) {
                        IconButton(onClick = { showShareSheet = true }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.statistics_share),
                            )
                        }
                    }
                },
            )
        }
    ) { paddingValues ->
        if (showShareSheet) {
            StatisticsShareSheet(
                stats = state.stats,
                shareText = stringResource(R.string.statistics_share_text),
                onDismiss = { showShareSheet = false },
            )
        }
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            state.error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.error ?: stringResource(R.string.statistics_unknown_error),
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> StatisticsContent(
                stats = state.stats,
                readingGoal = state.readingGoal,
                achievements = state.achievements,
                selectedPeriod = state.selectedPeriod,
                onSelectPeriod = { viewModel.onEvent(StatisticsEvent.SelectPeriod(it)) },
                downloadedChapterCount = state.downloadedChapterCount,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun StatisticsContent(
    stats: ReadingStats,
    readingGoal: ReadingGoal,
    achievements: List<Achievement>,
    selectedPeriod: StatsPeriod,
    onSelectPeriod: (StatsPeriod) -> Unit,
    downloadedChapterCount: Int?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { onSelectPeriod(period) },
                        label = {
                            Text(
                                when (period) {
                                    StatsPeriod.ALL -> stringResource(R.string.statistics_period_all)
                                    StatsPeriod.DAYS_90 -> stringResource(R.string.statistics_period_90d)
                                    StatsPeriod.DAYS_30 -> stringResource(R.string.statistics_period_30d)
                                    StatsPeriod.DAYS_7 -> stringResource(R.string.statistics_period_7d)
                                }
                            )
                        }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Gradient hero card — reading overview
        item { StatsHeroCard(stats = stats) }

        // Library detail — completed titles, total chapters (Komikku's Titles/Chapters sections)
        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            LibraryDetailCard(
                completedMangaCount = stats.completedMangaCount,
                totalChapterCount = stats.totalChapterCount,
                downloadedChapterCount = downloadedChapterCount,
            )
        }

        // Trackers — tracked titles, mean score, services (Komikku's Trackers section).
        // Hidden entirely when the user doesn't track anything.
        if (stats.trackedMangaCount > 0) {
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                TrackersCard(
                    trackedMangaCount = stats.trackedMangaCount,
                    meanTrackerScore = stats.meanTrackerScore,
                    trackerServiceCount = stats.trackerServiceCount,
                )
            }
        }

        // Reading goals progress
        if (readingGoal.dailyGoal > 0 || readingGoal.weeklyGoal > 0) {
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                SectionTitle(stringResource(R.string.statistics_reading_goals))
                Spacer(modifier = Modifier.height(8.dp))
                ReadingGoalsSection(readingGoal)
            }
        }

        // Reading streak
        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            StreakCard(
                currentStreak = stats.currentStreak,
                bestStreak = stats.bestStreak,
                readingActivityByDay = stats.readingActivityByDay.entries
                    .sortedBy { it.key }
                    .map { it.value }
            )
        }

        // Reading activity heatmap
        if (stats.readingActivityByDay.isNotEmpty()) {
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                SectionTitle(stringResource(R.string.statistics_reading_activity))
                Spacer(modifier = Modifier.height(8.dp))
                ReadingActivityGrid(activityByDay = stats.readingActivityByDay)
            }
        }

        // Genre distribution
        if (stats.genreDistribution.isNotEmpty()) {
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                SectionTitle(stringResource(R.string.statistics_top_genres))
                Spacer(modifier = Modifier.height(8.dp))
                GenreDistributionBars(genres = stats.genreDistribution)
            }
        }

        // Achievements
        if (achievements.isNotEmpty()) {
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                AchievementsSection(achievements = achievements)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/** Gradient hero card showing the three primary stats. */
@Composable
private fun StatsHeroCard(
    stats: ReadingStats,
    modifier: Modifier = Modifier
) {
    val otaku = LocalOtakuColors.current
    val gradientEnd = Color.hsl(290f, 0.65f, if (otaku.isDark) 0.28f else 0.72f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(otaku.accent, gradientEnd),
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.statistics_overview),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    HeroStatItem(
                        value = stats.totalMangaInLibrary.toString(),
                        label = stringResource(R.string.statistics_manga),
                        modifier = Modifier.weight(1f),
                    )
                    HeroStatItem(
                        value = stats.totalChaptersRead.toString(),
                        label = stringResource(R.string.statistics_chapters),
                        modifier = Modifier.weight(1f),
                    )
                    HeroStatItem(
                        value = formatReadingTime(stats.totalReadingTimeMs),
                        label = stringResource(R.string.statistics_reading_time),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ReadingGoalsSection(readingGoal: ReadingGoal) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (readingGoal.dailyGoal > 0) {
            GoalProgressCard(
                label = stringResource(R.string.statistics_daily_goal),
                progress = readingGoal.dailyProgress,
                goal = readingGoal.dailyGoal
            )
        }
        if (readingGoal.weeklyGoal > 0) {
            GoalProgressCard(
                label = stringResource(R.string.statistics_weekly_goal),
                progress = readingGoal.weeklyProgress,
                goal = readingGoal.weeklyGoal
            )
        }
    }
}

@Composable
private fun GoalProgressCard(
    label: String,
    progress: Int,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val fraction = (progress.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val isComplete = progress >= goal
    val otaku = LocalOtakuColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (isComplete) {
                    Text(
                        text = stringResource(R.string.statistics_goal_complete),
                        style = MaterialTheme.typography.bodyMedium,
                        color = otaku.success,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    MonoLabel(
                        text = stringResource(R.string.statistics_goal_progress, progress, goal),
                        color = otaku.fgMuted,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isComplete) otaku.success else otaku.accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun StreakCard(
    currentStreak: Int,
    bestStreak: Int,
    readingActivityByDay: List<Int>,
    modifier: Modifier = Modifier
) {
    val otaku = LocalOtakuColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.statistics_streak),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StreakStatItem(
                    value = stringResource(R.string.statistics_streak_days_short, currentStreak),
                    label = stringResource(R.string.statistics_current_streak),
                    valueColor = otaku.accent,
                    modifier = Modifier.weight(1f),
                )
                StreakStatItem(
                    value = stringResource(R.string.statistics_streak_days_short, bestStreak),
                    label = stringResource(R.string.statistics_best_streak),
                    valueColor = otaku.warning,
                    modifier = Modifier.weight(1f),
                )
            }
            // Mini bar chart of recent reading activity
            if (readingActivityByDay.isNotEmpty()) {
                MiniActivityBars(
                    values = readingActivityByDay.takeLast(30),
                    barColor = otaku.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
            }
        }
    }
}

/** Completed titles and total chapter count across the library — mirrors Komikku's Titles/Chapters stat sections. */
@Composable
private fun LibraryDetailCard(
    completedMangaCount: Int,
    totalChapterCount: Int,
    downloadedChapterCount: Int?,
    modifier: Modifier = Modifier
) {
    val otaku = LocalOtakuColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.statistics_library_detail),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StreakStatItem(
                    value = completedMangaCount.toString(),
                    label = stringResource(R.string.statistics_completed_titles),
                    valueColor = otaku.accent,
                    modifier = Modifier.weight(1f),
                )
                StreakStatItem(
                    value = totalChapterCount.toString(),
                    label = stringResource(R.string.statistics_total_chapters),
                    valueColor = otaku.accent,
                    modifier = Modifier.weight(1f),
                )
                if (downloadedChapterCount != null) {
                    StreakStatItem(
                        value = downloadedChapterCount.toString(),
                        label = stringResource(R.string.statistics_downloaded_chapters),
                        valueColor = otaku.accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackersCard(
    trackedMangaCount: Int,
    meanTrackerScore: Float?,
    trackerServiceCount: Int,
    modifier: Modifier = Modifier
) {
    val otaku = LocalOtakuColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.statistics_trackers),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StreakStatItem(
                    value = trackedMangaCount.toString(),
                    label = stringResource(R.string.statistics_tracked_titles),
                    valueColor = otaku.accent,
                    modifier = Modifier.weight(1f),
                )
                StreakStatItem(
                    value = meanTrackerScore
                        ?.let { String.format(Locale.getDefault(), "%.1f", it) }
                        ?: stringResource(R.string.statistics_no_score),
                    label = stringResource(R.string.statistics_mean_score),
                    valueColor = otaku.accent,
                    modifier = Modifier.weight(1f),
                )
                StreakStatItem(
                    value = trackerServiceCount.toString(),
                    label = stringResource(R.string.statistics_tracker_services),
                    valueColor = otaku.accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StreakStatItem(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Mini vertical bar chart for recent daily activity. */
@Composable
private fun MiniActivityBars(
    values: List<Int>,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxVal = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(modifier = modifier) {
        val barCount = values.size
        val totalGapWidth = size.width * 0.3f
        val barWidth = (size.width - totalGapWidth) / barCount
        val gapWidth = totalGapWidth / (barCount - 1).coerceAtLeast(1)
        values.forEachIndexed { index, value ->
            val fraction = value.toFloat() / maxVal
            val barHeight = (size.height * 0.8f * fraction).coerceAtLeast(4f)
            val x = index * (barWidth + gapWidth)
            val y = size.height - barHeight
            drawRoundRect(
                color = if (value > 0) barColor else barColor.copy(alpha = 0.15f),
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/** A simple activity grid – each cell represents one day, color intensity reflects activity count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingActivityGrid(activityByDay: Map<String, Int>) {
    val maxCount = activityByDay.values.maxOrNull() ?: 1
    val baseColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for ((_, count) in activityByDay.entries.sortedBy { it.key }) {
            val fraction = count.toFloat() / maxCount.toFloat()
            val cellColor = lerp(emptyColor, baseColor, 0.2f + fraction * 0.8f)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cellColor)
            )
        }
    }
}

/** Horizontal bar chart for top genres with multi-color accent progression. */
@Composable
private fun GenreDistributionBars(genres: Map<String, Int>) {
    val maxCount = genres.values.maxOrNull() ?: 1
    val otaku = LocalOtakuColors.current
    val accentColors = listOf(
        otaku.accent,
        otaku.accentSoft,
        otaku.warning,
        otaku.success,
        Color.hsl(200f, 0.7f, 0.55f),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        genres.entries
            .sortedByDescending { it.value }
            .take(MAX_TOP_GENRES)
            .forEachIndexed { index, (genre, count) ->
            GenreBar(
                genre = genre,
                count = count,
                fraction = count.toFloat() / maxCount.toFloat(),
                barColor = accentColors[index % accentColors.size],
            )
        }
    }
}

@Composable
private fun GenreBar(
    genre: String,
    count: Int,
    fraction: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            MonoLabel(
                text = count.toString(),
                color = barColor,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        ) {
            val barWidth = size.width * fraction
            drawRect(color = barColor.copy(alpha = 0.15f), size = size)
            drawRect(color = barColor, size = size.copy(width = barWidth))
        }
    }
}

private const val MAX_TOP_GENRES = 8

private fun formatReadingTime(ms: Long): String {
    if (ms == 0L) return "0m"
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}
