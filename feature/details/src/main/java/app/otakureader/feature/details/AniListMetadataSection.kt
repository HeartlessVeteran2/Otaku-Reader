package app.otakureader.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.MangaMetadata
import app.otakureader.domain.model.MangaMetadataTag
import java.text.NumberFormat

/**
 * The AniList half of the details screen: community stats, rank-weighted tags, alternative titles.
 *
 * ### It fills gaps; it never duplicates
 *
 * The screen already renders the *source's* description and genres, and the user can edit both.
 * Rendering AniList's copy alongside would show two of everything and put a third party's text next
 * to text the user deliberately corrected. So description and genres appear only when the local
 * record has none — handled at the call site in `DetailsScreen`, where both are already in hand —
 * and this file renders only what has no local equivalent at all.
 *
 * ### Absence is not an error
 *
 * Every field on [MangaMetadata] is nullable, and AniList genuinely lacks many of them for long-tail
 * entries. A missing value drops its cell; a metadata record with nothing in it renders nothing.
 * There is no error state here — see `DetailsViewModel.requestMetadataRefresh` for why a failed
 * fetch is silent.
 */
@Composable
internal fun AniListMetadataSection(
    metadata: MangaMetadata?,
    isLoading: Boolean,
    onTagClick: (String) -> Unit,
    onTagLongClick: (String) -> Unit,
    onRelationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (metadata == null && !isLoading) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.details_metadata_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (isLoading) {
                Spacer(modifier = Modifier.width(METADATA_HEADER_SPINNER_GAP))
                CircularProgressIndicator(
                    modifier = Modifier.size(METADATA_HEADER_SPINNER_SIZE),
                    strokeWidth = METADATA_HEADER_SPINNER_STROKE,
                )
            }
        }

        if (metadata == null) return@Column

        Spacer(modifier = Modifier.height(METADATA_SECTION_GAP))
        MetadataStatsGrid(metadata = metadata)

        if (metadata.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(METADATA_SECTION_GAP))
            MetadataTags(
                tags = metadata.tags,
                onTagClick = onTagClick,
                onTagLongClick = onTagLongClick,
            )
        }

        if (metadata.characters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(METADATA_SECTION_GAP))
            PersonCarousel(
                title = stringResource(R.string.details_metadata_characters),
                people = metadata.characters,
                // An AniList enum — MAIN, SUPPORTING, BACKGROUND — so it gets prettified.
                roleLabel = { it.prettifyEnum() },
            )
        }

        if (metadata.staff.isNotEmpty()) {
            Spacer(modifier = Modifier.height(METADATA_SECTION_GAP))
            PersonCarousel(
                title = stringResource(R.string.details_metadata_staff),
                people = metadata.staff,
                // Free text AniList stores verbatim ("Story & Art"). prettifyEnum would lowercase
                // the ampersand form into something AniList never said.
                roleLabel = { it },
            )
        }

        if (metadata.relations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(METADATA_SECTION_GAP))
            MangaRelationCarousel(
                relations = metadata.relations,
                onRelationClick = onRelationClick,
            )
        }

        if (metadata.externalLinks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(METADATA_SECTION_GAP))
            ExternalLinkChips(links = metadata.externalLinks)
        }

        if (metadata.synonyms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(METADATA_SECTION_GAP))
            MetadataSynonyms(synonyms = metadata.synonyms)
        }
    }
}

/**
 * Community numbers and publication facts, one cell per value AniList actually returned.
 *
 * A [FlowRow] rather than a fixed grid because the number of populated cells varies from one
 * (a bare entry with only a format) to eight, and a fixed grid would leave holes for the former.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataStatsGrid(metadata: MangaMetadata, modifier: Modifier = Modifier) {
    // Locale-aware grouping, so 145823 reads as "145,823". Remembered because NumberFormat
    // instances are not free and this composable can recompose with every scroll.
    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    val cells = buildList {
        metadata.averageScore?.let {
            add(stringResource(R.string.details_metadata_score) to stringResource(R.string.details_metadata_percent, it))
        }
        metadata.format?.let { add(stringResource(R.string.details_metadata_format) to it.prettifyEnum()) }
        metadata.status?.let { add(stringResource(R.string.details_metadata_status) to it.prettifyEnum()) }
        metadata.countryOfOrigin?.let { add(stringResource(R.string.details_metadata_origin) to countryLabel(it)) }
        metadata.chapters?.let {
            add(stringResource(R.string.details_metadata_chapters) to numberFormat.format(it))
        }
        metadata.popularity?.let {
            add(stringResource(R.string.details_metadata_popularity) to numberFormat.format(it))
        }
        metadata.favourites?.let {
            add(stringResource(R.string.details_metadata_favourites) to numberFormat.format(it))
        }
        publishedLabel(metadata)?.let { add(stringResource(R.string.details_metadata_published) to it) }
    }
    if (cells.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(METADATA_CELL_SPACING_HORIZONTAL),
        verticalArrangement = Arrangement.spacedBy(METADATA_CELL_SPACING_VERTICAL),
    ) {
        cells.forEach { (label, value) ->
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Tags with their community rank — "Isekai 87%".
 *
 * The rank is the whole point: it says how strongly the community thinks the tag applies, which a
 * bare genre list cannot. Tapping searches the tag in this manga's own source and long-pressing
 * searches every source, reusing the genre chips' events so a tag and a genre behave identically.
 *
 * Spoiler tags never reach here — they are filtered at the data boundary, in
 * `AniListMetadataRepository`, so nothing downstream has to remember to check.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataTags(
    tags: List<MangaMetadataTag>,
    onTagClick: (String) -> Unit,
    onTagLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.details_metadata_tags),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(METADATA_LABEL_GAP))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GENRE_CHIP_SPACING),
            verticalArrangement = Arrangement.spacedBy(GENRE_CHIP_SPACING),
        ) {
            // Highest-ranked first: the tags that actually characterise the work lead, instead of
            // whatever order AniList happened to return.
            tags.sortedByDescending { it.rank }.forEach { tag ->
                GenreChip(
                    text = stringResource(R.string.details_metadata_tag, tag.name, tag.rank),
                    onClick = { onTagClick(tag.name) },
                    onLongClick = { onTagLongClick(tag.name) },
                )
            }
        }
    }
}

/**
 * Every name AniList knows this work by — romaji, English, native, plus user-submitted synonyms,
 * already de-duplicated and stripped of placeholders upstream.
 *
 * The canonical title is in there too, and deliberately so: the *source's* title is frequently not
 * any of these, so a list that excluded AniList's own primary title would drop the one line that
 * tells the user their source is showing a translation or a romanisation of it.
 */
@Composable
private fun MetadataSynonyms(synonyms: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.details_metadata_synonyms),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(METADATA_LABEL_GAP))
        Text(
            text = synonyms.joinToString(SYNONYM_SEPARATOR),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The publication range, or null when AniList has no start date.
 *
 * An entry with a start and no end is still publishing, and renders as "2019 –" rather than
 * inventing an end date. Both dates are already ISO strings built from whatever precision AniList
 * gave — a year alone stays a year alone.
 */
@Composable
private fun publishedLabel(metadata: MangaMetadata): String? {
    val start = metadata.startDate ?: return null
    val end = metadata.endDate
    return if (end == null) {
        stringResource(R.string.details_metadata_published_ongoing, start)
    } else {
        stringResource(R.string.details_metadata_published_range, start, end)
    }
}

/**
 * A readable name for AniList's `countryOfOrigin`, which is what distinguishes manga from manhwa
 * and manhua. Unrecognised codes fall through as-is rather than being dropped — a code the user
 * can look up beats no row at all.
 */
@Composable
private fun countryLabel(code: String): String = when (code.uppercase()) {
    "JP" -> stringResource(R.string.details_metadata_country_jp)
    "KR" -> stringResource(R.string.details_metadata_country_kr)
    "CN" -> stringResource(R.string.details_metadata_country_cn)
    "TW" -> stringResource(R.string.details_metadata_country_tw)
    else -> code
}

/**
 * `ONE_SHOT` → `One Shot`.
 *
 * AniList returns GraphQL enum names, which are shouted and underscored. The no-argument
 * `lowercase()`/`uppercase()` use `Locale.ROOT`, deliberately: a locale-sensitive version would
 * turn `TITLE` into `tıtle` under a Turkish locale, and these are ASCII enum names, not user text.
 */
internal fun String.prettifyEnum(): String =
    split('_').joinToString(" ") { part ->
        part.lowercase().replaceFirstChar { it.uppercase() }
    }

private val METADATA_SECTION_GAP = 12.dp
private val METADATA_LABEL_GAP = 4.dp
private val METADATA_CELL_SPACING_HORIZONTAL = 24.dp
private val METADATA_CELL_SPACING_VERTICAL = 8.dp
private val METADATA_HEADER_SPINNER_GAP = 8.dp
private val METADATA_HEADER_SPINNER_SIZE = 14.dp
private val METADATA_HEADER_SPINNER_STROKE = 2.dp
private const val SYNONYM_SEPARATOR = " · "
