package app.otakureader.feature.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.MangaMetadataRelation
import coil3.compose.AsyncImage

/**
 * Sequels, prequels, side stories — other manga AniList links to this one.
 *
 * Every tile is tappable, and the tap runs a **global search for the title** rather than opening a
 * details screen. That is not a shortcut: a relation is an AniList media id, and the app has no
 * local record for it — it may not be in the library, and it may not exist on any installed
 * source. Searching is the honest action, it reuses the same effect the tag chips already use, and
 * it lands the user somewhere they can actually do something.
 *
 * The list is unkeyed, following [PersonCarousel]. Relations do not repeat an id the way staff
 * credits do, but a static read-only row gains nothing from keys and an id key is the failure mode
 * that crashed the screen once already.
 */
@Composable
internal fun MangaRelationCarousel(
    relations: List<MangaMetadataRelation>,
    onRelationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (relations.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.details_metadata_relations),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(RELATION_TITLE_GAP))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(RELATION_CARD_GAP)) {
            items(items = relations) { relation ->
                RelationCard(relation = relation, onClick = { onRelationClick(relation.title) })
            }
        }
    }
}

@Composable
private fun RelationCard(
    relation: MangaMetadataRelation,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(RELATION_CARD_WIDTH)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = relation.coverImage,
            contentDescription = relation.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(RELATION_COVER_RATIO)
                .clip(RoundedCornerShape(RELATION_CARD_CORNER)),
        )
        Spacer(modifier = Modifier.height(RELATION_IMAGE_GAP))
        relation.relationType?.let { type ->
            Text(
                text = type.prettifyEnum(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = relation.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = RELATION_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        relation.format?.let { format ->
            Text(
                text = format.prettifyEnum(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private val RELATION_CARD_WIDTH = 96.dp
private val RELATION_CARD_GAP = 12.dp
private val RELATION_TITLE_GAP = 8.dp
private val RELATION_IMAGE_GAP = 4.dp
private val RELATION_CARD_CORNER = 8.dp

/** Manga covers are conventionally 2:3, the same as the portraits in [PersonCarousel]. */
private const val RELATION_COVER_RATIO = 2f / 3f
private const val RELATION_TITLE_MAX_LINES = 2
