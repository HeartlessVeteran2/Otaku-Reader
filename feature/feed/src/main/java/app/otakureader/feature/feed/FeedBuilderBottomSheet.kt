package app.otakureader.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Height cap for the source list so the sheet cannot grow past roughly half the screen. */
private val SourceListMaxHeight = 320.dp

/**
 * Picks a feed source from the installed sources.
 *
 * This used to be a free-text field whose contents were hashed into a source id, which could
 * never match a real source — so every row it wrote was unusable and a typo looked identical to
 * a correct entry. Sources the user has already added are filtered out upstream, so every row
 * here is addable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedBuilderBottomSheet(
    availableSources: List<FeedSourceOption>,
    onDismiss: () -> Unit,
    onAddSource: (FeedSourceOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(availableSources, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            availableSources
        } else {
            availableSources.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.feed_add_source),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            if (availableSources.isEmpty()) {
                Text(
                    text = stringResource(R.string.feed_no_available_sources),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.feed_search_sources)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.heightIn(max = SourceListMaxHeight)) {
                    items(filtered, key = { it.sourceId }) { source ->
                        ListItem(
                            headlineContent = { Text(source.name) },
                            supportingContent = source.lang
                                .takeIf { it.isNotBlank() }
                                ?.let { lang -> { Text(lang.uppercase()) } },
                            modifier = Modifier.clickable { onAddSource(source) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.feed_cancel))
                }
            }
        }
    }
}
