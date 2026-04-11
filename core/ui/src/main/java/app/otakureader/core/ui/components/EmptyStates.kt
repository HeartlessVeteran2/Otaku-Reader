package app.otakureader.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.R
import kotlinx.coroutines.delay

/**
 * Enhanced empty state component with animations and actions.
 *
 * @param icon The icon to display
 * @param title The title text
 * @param message Optional subtitle message
 * @param primaryActionText Text for primary action button (null to hide)
 * @param onPrimaryAction Callback for primary action
 * @param secondaryActionText Text for secondary action button (null to hide)
 * @param onSecondaryAction Callback for secondary action
 * @param modifier Modifier for customization
 */
@Composable
fun EnhancedEmptyState(
    icon: ImageVector,
    title: String,
    message: String?,
    primaryActionText: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100) // Small delay for entrance animation
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon with background
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .alpha(0.6f),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            // Message
            if (message != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary action
            if (primaryActionText != null && onPrimaryAction != null) {
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(primaryActionText)
                }
            }

            // Secondary action
            if (secondaryActionText != null && onSecondaryAction != null) {
                if (primaryActionText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = onSecondaryAction,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(secondaryActionText)
                }
            }
        }
    }
}

/**
 * Pre-configured empty state for Library screen.
 */
@Composable
fun EmptyLibraryState(
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EnhancedEmptyState(
        icon = Icons.Outlined.Book,
        title = stringResource(R.string.empty_library_title),
        message = stringResource(R.string.empty_library_message),
        primaryActionText = stringResource(R.string.empty_library_action),
        onPrimaryAction = onBrowseClick,
        modifier = modifier
    )
}

/**
 * Pre-configured empty state for Browse/Search screen.
 */
@Composable
fun EmptySearchState(
    onClearSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EnhancedEmptyState(
        icon = Icons.Default.Search,
        title = stringResource(R.string.empty_search_title),
        message = stringResource(R.string.empty_search_message),
        primaryActionText = if (onClearSearch != null) stringResource(R.string.empty_search_action) else null,
        onPrimaryAction = onClearSearch,
        modifier = modifier
    )
}

/**
 * Pre-configured empty state for History screen.
 */
@Composable
fun EmptyHistoryState(
    onBrowseClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EnhancedEmptyState(
        icon = Icons.Default.History,
        title = stringResource(R.string.empty_history_title),
        message = stringResource(R.string.empty_history_message),
        primaryActionText = if (onBrowseClick != null) stringResource(R.string.empty_history_action) else null,
        onPrimaryAction = onBrowseClick,
        modifier = modifier
    )
}

/**
 * Pre-configured empty state for Updates screen.
 */
@Composable
fun EmptyUpdatesState(
    modifier: Modifier = Modifier
) {
    EnhancedEmptyState(
        icon = Icons.Default.NewReleases,
        title = stringResource(R.string.empty_updates_title),
        message = stringResource(R.string.empty_updates_message),
        modifier = modifier
    )
}

/**
 * Pre-configured empty state for Favorites/Bookmarks.
 */
@Composable
fun EmptyFavoritesState(
    onBrowseClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EnhancedEmptyState(
        icon = Icons.Default.Favorite,
        title = stringResource(R.string.empty_favorites_title),
        message = stringResource(R.string.empty_favorites_message),
        primaryActionText = if (onBrowseClick != null) stringResource(R.string.empty_favorites_action) else null,
        onPrimaryAction = onBrowseClick,
        modifier = modifier
    )
}

/**
 * Pre-configured empty state for Downloads screen.
 */
@Composable
fun EmptyDownloadsState(
    modifier: Modifier = Modifier
) {
    EnhancedEmptyState(
        icon = Icons.Default.Book,
        title = stringResource(R.string.empty_downloads_title),
        message = stringResource(R.string.empty_downloads_message),
        modifier = modifier
    )
}

/**
 * Pre-configured offline/no network state.
 */
@Composable
fun OfflineState(
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EnhancedEmptyState(
        icon = Icons.Default.WifiOff,
        title = stringResource(R.string.offline_title),
        message = stringResource(R.string.offline_message),
        primaryActionText = if (onRetry != null) stringResource(R.string.offline_action) else null,
        onPrimaryAction = onRetry,
        modifier = modifier
    )
}
