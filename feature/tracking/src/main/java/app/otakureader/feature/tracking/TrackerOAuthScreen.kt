package app.otakureader.feature.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * OAuth callback screen for tracker authentication.
 *
 * This screen is shown when the browser redirects back to the app after the
 * user approves tracker access. It exchanges the authorization code for an
 * access token and reports success or failure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerOAuthScreen(
    tracker: String,
    code: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    callbackState: String? = null,
    viewModel: TrackerOAuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val trackerDisplayName = trackerName(tracker)

    val linkedSnackbar = stringResource(R.string.tracking_oauth_linked_snackbar, trackerDisplayName)

    LaunchedEffect(tracker, code) {
        viewModel.exchangeCode(tracker, code, callbackState)
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            snackbarHostState.showSnackbar(linkedSnackbar)
            kotlinx.coroutines.delay(1500)
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tracking_oauth_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.tracking_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.tracking_oauth_linking, trackerDisplayName),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "❌",
                            style = MaterialTheme.typography.displayMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.tracking_oauth_link_failed, trackerDisplayName),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error ?: stringResource(R.string.tracking_oauth_unknown_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.exchangeCode(tracker, code, callbackState) }) {
                            Text(stringResource(R.string.tracking_oauth_retry))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateBack) {
                            Text(stringResource(R.string.tracking_oauth_go_back))
                        }
                    }
                }

                state.success -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "✅",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.tracking_oauth_linked_success, trackerDisplayName),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

private fun trackerName(tracker: String): String = when (tracker) {
    "anilist" -> "AniList"
    "mal" -> "MyAnimeList"
    "kitsu" -> "Kitsu"
    "shikimori" -> "Shikimori"
    else -> tracker.replaceFirstChar { it.uppercase() }
}
