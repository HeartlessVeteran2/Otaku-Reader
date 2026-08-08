package app.otakureader.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.AniListMediaCandidate
import coil3.compose.AsyncImage

/**
 * Lets the user say which AniList entry a manga actually is.
 *
 * ### Why this exists at all
 *
 * Auto-matching refuses to guess: below `MatchAniListMediaUseCase.ACCEPT_THRESHOLD` nothing is
 * stored and nothing renders, because a wrong synopsis and wrong tags look authoritative and give
 * the user no reason to doubt them. That is the right default and it leaves the user with no
 * metadata and no recourse — this is the recourse.
 *
 * A pick is recorded as `userConfirmed`, which auto-matching then refuses to overwrite. Without
 * that flag the next visit would replace the correction with the guess that made it necessary.
 *
 * ### Why each row shows a cover, a format and a year
 *
 * A list reading "Kaguya-sama wa Kokurasetai" three times over is not a choice anyone can make.
 * Those three fields are what separate a work from its sequel, its colored edition and its
 * spin-off, and they are the only reason the search query fetches more than titles.
 */
@Composable
internal fun AniListPickerDialog(
    picker: DetailsContract.AniListPickerState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_anilist_picker_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = picker.query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.details_anilist_picker_search_label)) },
                    singleLine = true,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(PICKER_GAP))
                when {
                    picker.isSearching -> Box(
                        modifier = Modifier.fillMaxWidth().height(PICKER_STATUS_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    // A failed search and an empty one are different answers with different next
                    // steps, so they never share a message.
                    picker.searchFailed -> Text(
                        // Always the generic string. The exception's own message is written for a
                        // developer, not a user, and never reaches the screen — see searchFailed.
                        text = stringResource(R.string.details_anilist_picker_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    picker.isEmpty -> Text(
                        text = stringResource(R.string.details_anilist_picker_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = PICKER_LIST_MAX_HEIGHT),
                        verticalArrangement = Arrangement.spacedBy(PICKER_GAP),
                    ) {
                        items(picker.results, key = { it.mediaId }) { candidate ->
                            AniListCandidateRow(
                                candidate = candidate,
                                onClick = { onSelect(candidate.mediaId) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSearch) {
                Text(stringResource(R.string.details_anilist_picker_search))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.details_anilist_picker_cancel))
            }
        },
    )
}

@Composable
private fun AniListCandidateRow(
    candidate: AniListMediaCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = PICKER_ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A null cover would leave AsyncImage occupying its reserved space with nothing in it,
        // which reads as a broken row rather than an entry without artwork.
        val coverModifier = Modifier.width(PICKER_COVER_WIDTH).aspectRatio(COVER_ASPECT_RATIO)
        if (candidate.coverImage != null) {
            AsyncImage(
                model = candidate.coverImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = coverModifier,
            )
        } else {
            Box(
                modifier = coverModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(PICKER_GAP))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                // displayTitle is null when AniList gave this entry no usable name at all. Showing
                // an empty row would leave a tappable target with nothing to identify it.
                text = candidate.displayTitle
                    ?: stringResource(R.string.details_anilist_picker_untitled),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = candidateSubtitle(candidate)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Manga · 2019", dropping whatever AniList did not supply.
 *
 * The year is the single most useful disambiguator between a work and its sequel, so it is here
 * rather than buried — but an entry with neither field still renders its title cleanly rather than
 * a stray separator.
 */
private fun candidateSubtitle(candidate: AniListMediaCandidate): String =
    listOfNotNull(
        candidate.format?.let { format ->
            format.split('_').joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
        },
        candidate.startYear?.toString(),
    ).joinToString(SUBTITLE_SEPARATOR)

private val PICKER_GAP = 8.dp
private val PICKER_ROW_PADDING = 4.dp
private val PICKER_COVER_WIDTH = 40.dp
private val PICKER_STATUS_HEIGHT = 96.dp
private val PICKER_LIST_MAX_HEIGHT = 320.dp
private const val COVER_ASPECT_RATIO = 0.7f
private const val SUBTITLE_SEPARATOR = " · "
