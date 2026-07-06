package app.otakureader.core.webview

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.navigation.Route

/**
 * Full-screen embedded WebView with optional ad blocking.
 *
 * @param url          URL to load immediately.
 * @param purpose      Why this WebView is being shown (e.g. CAPTCHA or GENERAL).
 * @param onClose      Called when the user taps the close / back button.
 *                     Receives the current cookie string for [url] so the caller
 *                     can forward it to the extension that triggered the WebView.
 * @param modifier     Optional [Modifier].
 * @param viewModel    Hilt-injected [WebViewViewModel]; override in tests.
 */
@Composable
fun WebViewScreen(
    url: String,
    @Suppress("UnusedParameter") purpose: WebViewPurpose,
    onClose: (cookieString: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebViewViewModel = hiltViewModel(),
) {
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle(initialValue = true)
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        WebViewTopBar(
            onBack = { onClose(webViewRef?.let { viewModel.cookiesForUrl(url) }) },
            onNavigateBack = { webViewRef?.goBack() },
            onNavigateForward = { webViewRef?.goForward() },
            onReload = { webViewRef?.reload() },
            adBlockEnabled = adBlockEnabled,
            onToggleAdBlock = { viewModel.toggleAdBlock() },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).also { wv ->
                        webViewRef = wv
                        WebViewSession(
                            context = context,
                            adBlockEnabled = adBlockEnabled,
                            onPageFinished = { /* no-op — callers get cookies via onClose */ },
                        ).configure(wv)
                        // configure() already sets allowContentAccess = false; repeat here
                        // so static analysis tools (CodeQL) can verify it without tracing
                        // through the opaque configure() call.
                        wv.settings.allowContentAccess = false
                        wv.loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewTopBar(
    onBack: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onReload: () -> Unit,
    adBlockEnabled: Boolean,
    onToggleAdBlock: () -> Unit,
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close",
                )
            }
        },
        actions = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                )
            }
            IconButton(onClick = onNavigateForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Navigate forward",
                )
            }
            IconButton(onClick = onReload) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reload",
                )
            }

            var showMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (adBlockEnabled) "Disable ad blocking" else "Enable ad blocking",
                        )
                    },
                    onClick = {
                        onToggleAdBlock()
                        showMenu = false
                    },
                )
            }
        },
    )
}

/**
 * Registers the [WebViewScreen] destination in the app's [NavGraphBuilder].
 *
 * The [onClose] callback receives the source ID, the URL, and the cookie string
 * so that callers (e.g. extension WebView bridges) can act on the result.
 */
fun NavGraphBuilder.webViewScreen(
    onClose: (sourceId: Long, url: String, cookieString: String?) -> Unit,
) {
    composable<Route.WebView> { backStackEntry ->
        val route = backStackEntry.toRoute<Route.WebView>()
        WebViewScreen(
            url = route.url,
            purpose = runCatching { WebViewPurpose.valueOf(route.purpose) }
                .getOrDefault(WebViewPurpose.GENERAL),
            onClose = { cookies -> onClose(route.sourceId, route.url, cookies) },
        )
    }
}
