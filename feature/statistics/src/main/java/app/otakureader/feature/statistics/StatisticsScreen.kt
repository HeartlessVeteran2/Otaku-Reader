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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.ReadingGoal
import app.otakureader.domain.model.ReadingStats

/** Statistics screen showing reading analytics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.statistics_back))
                    }
                }
            )
        }
    ) { paddingValues ->
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
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun StatisticsContent(
    stats: ReadingStats,
    readingGoal: ReadingGoal,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Reading goals progress
        if (readingGoal.dailyGoal > 0 || readingGoal.weeklyGoal > 0) {
            item {
                SectionTitle(stringResource(R.string.statistics_reading_goals))
                Spacer(modifier = Modifier.height(8.dp))
                ReadingGoalsSection(readingGoal)
            }
        }

        // Summary cards
        item {
            if (readingGoal.dailyGoal > 0 || readingGoal.weeklyGoal > 0) {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
            }
            SectionTitle(stringResource(R.string.statistics_overview))
            Spacer(modifier = Modifier.height(8.dp))
            SummaryCards(stats)
        }

        // Reading streak
        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            SectionTitle(stringResource(R.string.statistics_reading_streak))
            Spacer(modifier = Modifier.height(8.dp))
            StreakRow(currentStreak = stats.currentStreak, bestStreak = stats.bestStreak)
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

        item { Spacer(modifier = Modifier.height(16.dp)) }
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
private fun SummaryCards(stats: ReadingStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            label = stringResource(R.string.statistics_manga),
            value = stats.totalMangaInLibrary.toString(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = stringResource(R.string.statistics_chapters),
            value = stats.totalChaptersRead.toString(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = stringResource(R.string.statistics_reading_time),
            value = formatReadingTime(stats.totalReadingTimeMs),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StreakRow(currentStreak: Int, bestStreak: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            label = stringResource(R.string.statistics_current_streak),
            value = "${currentStreak}d",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = stringResource(R.string.statistics_best_streak),
            value = "${bestStreak}d",
            modifier = Modifier.weight(1f)
        )
    }
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
                Text(
                    text = if (isComplete) stringResource(R.string.statistics_goal_complete) else stringResource(R.string.statistics_goal_progress, progress, goal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isComplete) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isComplete) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
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

/** Horizontal bar chart for top genres. */
@Composable
private fun GenreDistributionBars(genres: Map<String, Int>) {
    val maxCount = genres.values.maxOrNull() ?: 1
    val barColor = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((genre, count) in genres) {
            GenreBar(
                genre = genre,
                count = count,
                fraction = count.toFloat() / maxCount.toFloat(),
                barColor = barColor
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        ) {
            val barWidth = size.width * fraction
            drawRect(color = barColor.copy(alpha = 0.2f), size = size)
            drawRect(color = barColor, size = size.copy(width = barWidth))
        }
    }
}

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
