package app.otakureader.feature.details

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.otakureader.core.common.network.isBrowsableHttpUrl
import app.otakureader.domain.model.MangaMetadataExternalLink

/**
 * Off-site links AniList holds for this manga — official site, publisher, store.
 *
 * A [FlowRow] of chips rather than a carousel: there are usually two or three, they are short, and
 * wrapping reads better than a row that scrolls for one item past the edge.
 *
 * ### The scheme is checked again here
 *
 * The repository already refuses anything that is not `http`/`https` before caching it, using the
 * same shared predicate, so in normal operation this check never fires. It is not redundant. The cache is a table on the
 * device, rows written by an older build predate that filter, and a restored backup can carry
 * rows this build never fetched — so the value reaching this composable has not necessarily passed
 * through today's mapper. Validating at the point where a URL becomes an `Intent` is the check
 * that cannot be bypassed by where the data came from.
 *
 * A chip that fails the check is not rendered at all, rather than rendered and refused on tap. A
 * control whose only behaviour is to reject the user is worse than an absent one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExternalLinkChips(
    links: List<MangaMetadataExternalLink>,
    modifier: Modifier = Modifier,
) {
    val browsable = links.filter { it.url.isBrowsableHttpUrl() }
    if (browsable.isEmpty()) return

    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.details_metadata_external_links),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(LINK_TITLE_GAP))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LINK_CHIP_GAP),
            verticalArrangement = Arrangement.spacedBy(LINK_CHIP_GAP),
        ) {
            browsable.forEach { link ->
                AssistChip(
                    onClick = {
                        // runCatching, not a try/catch around a specific type: the device may have
                        // no browser at all, and a details screen must not crash because a link
                        // had nowhere to go.
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri()))
                        }
                    },
                    label = {
                        Text(
                            text = link.site,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    border = AssistChipDefaults.assistChipBorder(enabled = true),
                )
            }
        }
    }
}

private val LINK_TITLE_GAP = 8.dp
private val LINK_CHIP_GAP = 8.dp
