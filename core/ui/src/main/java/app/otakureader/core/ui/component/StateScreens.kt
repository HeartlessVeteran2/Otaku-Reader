package app.otakureader.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.theme.LocalOtakuColors
import app.otakureader.core.ui.R

/** Full-screen loading indicator. */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/** Full-screen loading indicator with message. */
@Composable
fun LoadingScreen(
    message: String,
    modifier: Modifier = Modifier
) {
    val otaku = LocalOtakuColors.current
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = otaku.fgMuted
        )
    }
}

/** Full-screen error state with optional retry action. */
@Composable
fun ErrorScreen(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onOpenInBrowser: (() -> Unit)? = null,
) {
    ErrorScreen(
        icon = Icons.Default.Error,
        title = stringResource(R.string.core_ui_error_title),
        message = message,
        modifier = modifier,
        onRetry = onRetry,
        onOpenInBrowser = onOpenInBrowser,
    )
}

/** Full-screen error state with full customization. */
@Composable
fun ErrorScreen(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    retryText: String = stringResource(R.string.core_ui_retry),
    onRetry: (() -> Unit)? = null,
    /** Secondary "try in browser" escape hatch; null hides the button. */
    onOpenInBrowser: (() -> Unit)? = null,
    openInBrowserText: String = stringResource(R.string.core_ui_open_in_browser),
) {
    val otaku = LocalOtakuColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            // Decorative: the title Text below carries the meaning; a description here
            // would make TalkBack announce the error twice.
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = otaku.danger
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = otaku.fg
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = otaku.fgMuted,
            textAlign = TextAlign.Center
        )

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(retryText)
            }
        }

        if (onOpenInBrowser != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenInBrowser) {
                Text(openInBrowserText)
            }
        }
    }
}

/** Full-screen empty state with message. */
@Composable
fun EmptyScreen(
    message: String,
    modifier: Modifier = Modifier
) {
    EmptyScreen(
        icon = Icons.Default.HourglassEmpty,
        title = message,
        message = null,
        modifier = modifier
    )
}

/** Full-screen empty state with full customization. */
@Composable
fun EmptyScreen(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val otaku = LocalOtakuColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(64.dp),
            tint = otaku.fgMuted.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = otaku.fg,
            textAlign = TextAlign.Center
        )

        if (message != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = otaku.fgMuted,
                textAlign = TextAlign.Center
            )
        }

        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}
