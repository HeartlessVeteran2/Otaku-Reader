package app.otakureader.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.MangaMetadataPerson
import coil3.compose.AsyncImage

/**
 * A horizontal strip of faces with a name and a role under each — the cast, or the credits.
 *
 * One composable for both because the data is one shape ([MangaMetadataPerson]). What differs is
 * how `role` reads, and that is settled *before* it gets here: the caller passes [roleLabel].
 * Characters carry an AniList enum (`MAIN`) that wants prettifying; staff carry free text
 * ("Story & Art") that must be shown verbatim. Deciding that inside would mean guessing which
 * kind of list this is from the values in it, which is exactly the guess that breaks on a staff
 * credit that happens to read "Main".
 *
 * @param roleLabel formats a person's raw role for display, or returns null to show no role line.
 */
@Composable
internal fun PersonCarousel(
    title: String,
    people: List<MangaMetadataPerson>,
    roleLabel: (String) -> String?,
    modifier: Modifier = Modifier,
) {
    if (people.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(PERSON_TITLE_GAP))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(PERSON_CARD_GAP),
            contentPadding = PaddingValues(horizontal = PERSON_ROW_EDGE_PADDING),
        ) {
            // Keyed by AniList's person id so recomposition survives the list being replaced
            // wholesale by a metadata refresh, which is the common case here.
            items(items = people, key = { it.id }) { person ->
                PersonCard(person = person, roleLabel = roleLabel)
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: MangaMetadataPerson,
    roleLabel: (String) -> String?,
) {
    Column(modifier = Modifier.width(PERSON_CARD_WIDTH)) {
        // No error/placeholder painter: a missing portrait is common and unremarkable, and Coil
        // leaves the bounds empty rather than collapsing them, so the row keeps its rhythm.
        AsyncImage(
            model = person.imageUrl,
            contentDescription = person.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PERSON_PORTRAIT_RATIO)
                .clip(RoundedCornerShape(PERSON_CARD_CORNER)),
        )
        Spacer(modifier = Modifier.height(PERSON_IMAGE_GAP))
        Text(
            text = person.name,
            style = MaterialTheme.typography.bodySmall,
            // Two lines because a full name routinely wraps at this width and truncating to one
            // would cut most Japanese names mid-surname.
            maxLines = PERSON_NAME_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        person.role?.let(roleLabel)?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val PERSON_CARD_WIDTH = 84.dp
private val PERSON_CARD_GAP = 12.dp
private val PERSON_ROW_EDGE_PADDING = 0.dp
private val PERSON_TITLE_GAP = 8.dp
private val PERSON_IMAGE_GAP = 4.dp
private val PERSON_CARD_CORNER = 8.dp

/** AniList portraits are a consistent 2:3, so cropping to it never cuts a face. */
private const val PERSON_PORTRAIT_RATIO = 2f / 3f
private const val PERSON_NAME_MAX_LINES = 2
