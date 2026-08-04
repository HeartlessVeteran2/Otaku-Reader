package app.otakureader.feature.details

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import java.util.Locale

/**
 * One chip per linked tracker, showing what that service currently holds.
 *
 * ### Why this replaces a count
 *
 * The action row's tracking button showed "2 trackers" and nothing else, while the ViewModel was
 * already collecting the full entries and keeping only `.size`. Everything a chip needs — status,
 * progress, score — was being fetched and discarded on every emission.
 *
 * Tapping any chip opens the tracking screen, the same destination the count button already had, so
 * this adds information without adding a new place to get lost.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrackerChips(
    entries: List<TrackEntry>,
    trackerNames: Map<Int, String>,
    onOpenTracking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TRACKER_CHIP_SPACING),
        verticalArrangement = Arrangement.spacedBy(TRACKER_CHIP_SPACING),
    ) {
        entries.forEach { entry ->
            TrackerChip(
                entry = entry,
                // An entry whose tracker isn't in the registered set would otherwise render a
                // nameless chip. That should not happen — entries are written by the trackers
                // themselves — but a generic label is a better failure than a blank one.
                trackerName = trackerNames[entry.trackerId]
                    ?: stringResource(R.string.details_tracker_unknown),
                onClick = onOpenTracking,
            )
        }
    }
}

@Composable
private fun TrackerChip(
    entry: TrackEntry,
    trackerName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = TRACKER_CHIP_PADDING_HORIZONTAL,
                vertical = TRACKER_CHIP_PADDING_VERTICAL,
            )
        ) {
            Text(text = trackerName, style = MaterialTheme.typography.labelMedium)
            Text(
                text = entry.detailLine(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = TRACKER_CHIP_DETAIL_ALPHA),
            )
        }
    }
}

/**
 * The second line of a chip: status, progress, and score, dropping whatever the service has not set.
 *
 * A score of 0 means "not rated", not "rated zero", so it is omitted rather than shown as a real
 * rating of the worst possible value. That reading is the one the write path already takes:
 * `KitsuTracker` sends `ratingTwenty = null` for any score at or below zero, and both
 * `AniListTracker` and `MyAnimeListTracker` map a missing remote score to `0f`.
 *
 * Progress is dropped only when both counters are zero, because "3/?" is still worth showing for a
 * series whose length the service does not know.
 */
@Composable
private fun TrackEntry.detailLine(): String {
    val parts = buildList {
        add(stringResource(status.labelRes()))
        if (lastChapterRead > 0f || totalChapters > 0) {
            val total = if (totalChapters > 0) {
                totalChapters.toString()
            } else {
                stringResource(R.string.details_tracker_total_unknown)
            }
            add(stringResource(R.string.details_tracker_progress, lastChapterRead.formatChapter(), total))
        }
        if (score > 0f) add(stringResource(R.string.details_tracker_score, score.formatScore()))
    }
    return parts.joinToString(TRACKER_CHIP_SEPARATOR)
}

/** Chapter numbers are `Float` because half-chapters exist; whole ones should not read "12.0". */
private fun Float.formatChapter(): String =
    if (this % 1f == 0f) toInt().toString() else String.format(Locale.getDefault(), "%.1f", this)

/** Scores are canonically 0–10 across every tracker, so one decimal is the right precision. */
private fun Float.formatScore(): String = String.format(Locale.getDefault(), "%.1f", this)

@StringRes
private fun TrackStatus.labelRes(): Int = when (this) {
    TrackStatus.READING -> R.string.track_status_reading
    TrackStatus.COMPLETED -> R.string.track_status_completed
    TrackStatus.ON_HOLD -> R.string.track_status_on_hold
    TrackStatus.DROPPED -> R.string.track_status_dropped
    TrackStatus.PLAN_TO_READ -> R.string.track_status_plan_to_read
    TrackStatus.RE_READING -> R.string.track_status_re_reading
}

private val TRACKER_CHIP_SPACING = 8.dp
private val TRACKER_CHIP_PADDING_HORIZONTAL = 12.dp
private val TRACKER_CHIP_PADDING_VERTICAL = 6.dp
private const val TRACKER_CHIP_DETAIL_ALPHA = 0.75f
private const val TRACKER_CHIP_SEPARATOR = " · "
