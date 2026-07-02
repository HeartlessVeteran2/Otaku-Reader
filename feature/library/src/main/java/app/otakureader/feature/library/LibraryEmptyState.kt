package app.otakureader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.theme.OtakuReaderTheme

/** Shared spacing/sizing for the library empty-state composables. */
private object EmptyStateDefaults {
    val ContentPadding = 32.dp
    val IconSize = 64.dp
    val TitleSpacing = 16.dp
    val MessageSpacing = 8.dp
    val CtaSpacing = 24.dp
    val CtaIconSize = 18.dp
    val CtaIconGap = 8.dp
}

@Composable
internal fun EmptyLibraryMessage(
    modifier: Modifier = Modifier,
    onBrowseClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(EmptyStateDefaults.ContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = stringResource(R.string.library_empty_icon_description),
            modifier = Modifier.size(EmptyStateDefaults.IconSize),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(EmptyStateDefaults.TitleSpacing))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(EmptyStateDefaults.MessageSpacing))
        Text(
            text = stringResource(R.string.library_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onBrowseClick != null) {
            Spacer(modifier = Modifier.height(EmptyStateDefaults.CtaSpacing))
            Button(onClick = onBrowseClick) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(EmptyStateDefaults.CtaIconSize),
                )
                Spacer(modifier = Modifier.width(EmptyStateDefaults.CtaIconGap))
                Text(stringResource(R.string.library_empty_browse_cta))
            }
        }
    }
}

@Composable
internal fun EmptyLibrarySearchMessage(
    query: String,
    modifier: Modifier = Modifier,
    onBrowseClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(EmptyStateDefaults.ContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(EmptyStateDefaults.IconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(EmptyStateDefaults.TitleSpacing))
        Text(
            text = stringResource(R.string.library_search_no_results_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(EmptyStateDefaults.MessageSpacing))
        Text(
            text = stringResource(R.string.library_search_no_results_message, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onBrowseClick != null) {
            Spacer(modifier = Modifier.height(EmptyStateDefaults.CtaSpacing))
            Button(onClick = onBrowseClick) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(EmptyStateDefaults.CtaIconSize),
                )
                Spacer(modifier = Modifier.width(EmptyStateDefaults.CtaIconGap))
                Text(stringResource(R.string.library_search_globally))
            }
        }
    }
}

/**
 * Shown when the library has manga but active filters hide all of them — matches
 * Komikku/Mihon's "error_no_match" empty state. Offers a one-tap clear-filters action
 * so the user doesn't have to navigate back into the settings sheet.
 */
@Composable
internal fun EmptyLibraryFilterMessage(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(EmptyStateDefaults.ContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = null,
            modifier = Modifier.size(EmptyStateDefaults.IconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(EmptyStateDefaults.TitleSpacing))
        Text(
            text = stringResource(R.string.library_filter_no_results_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(EmptyStateDefaults.MessageSpacing))
        Text(
            text = stringResource(R.string.library_filter_no_results_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(EmptyStateDefaults.CtaSpacing))
        Button(onClick = onClearFilters) {
            Text(stringResource(R.string.library_filter_no_results_clear_cta))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12121A)
@Composable
private fun EmptyLibraryMessagePreview() {
    OtakuReaderTheme {
        EmptyLibraryMessage()
    }
}
