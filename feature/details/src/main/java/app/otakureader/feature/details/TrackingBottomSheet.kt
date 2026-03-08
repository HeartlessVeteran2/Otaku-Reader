package app.otakureader.feature.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.TrackItem
import app.otakureader.domain.model.TrackService

/**
 * Bottom sheet that shows tracking status for each supported service and allows
 * the user to log in, search for a manga entry, and link it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingBottomSheet(
    state: DetailsContract.State,
    onEvent: (DetailsContract.Event) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Tracking",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TrackService.entries.forEach { service ->
                val trackItem = state.tracks.find { it.service == service }
                val loginState = state.trackLoginStates[service]

                TrackServiceRow(
                    service = service,
                    trackItem = trackItem,
                    isLoggedIn = loginState?.isLoggedIn == true,
                    isLoading = loginState?.isLoading == true,
                    searchResults = state.trackSearchResults,
                    isSearching = state.isSearchingTrack,
                    onLoginClick = { onEvent(DetailsContract.Event.TrackLogin(service)) },
                    onLogoutClick = { onEvent(DetailsContract.Event.TrackLogout(service)) },
                    onSearchManga = { query ->
                        onEvent(DetailsContract.Event.SearchTrackManga(service, query))
                    },
                    onSelectResult = { result ->
                        onEvent(DetailsContract.Event.LinkTrack(result))
                    },
                    onUnlink = { onEvent(DetailsContract.Event.UnlinkTrack(service)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun TrackServiceRow(
    service: TrackService,
    trackItem: TrackItem?,
    isLoggedIn: Boolean,
    isLoading: Boolean,
    searchResults: List<TrackItem>,
    isSearching: Boolean,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSearchManga: (String) -> Unit,
    onSelectResult: (TrackItem) -> Unit,
    onUnlink: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember(trackItem) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                if (trackItem != null) {
                    Text(
                        text = trackItem.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ch. ${trackItem.lastChapterRead.toInt()} · ${trackItem.status.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!isLoggedIn) {
                    Text(
                        text = "Not logged in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Not tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                !isLoggedIn -> Button(onClick = onLoginClick) { Text("Log in") }
                trackItem != null -> {
                    IconButton(onClick = onUnlink) {
                        Icon(Icons.Default.Close, contentDescription = "Unlink")
                    }
                }
                else -> {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Add, contentDescription = "Track")
                    }
                }
            }
        }

        if (isLoggedIn && showSearch) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search manga…") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = { onSearchManga(searchQuery) }) {
                        Icon(Icons.Default.Check, contentDescription = "Search")
                    }
                }
            }

            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                searchResults.take(5).forEach { result ->
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectResult(result)
                                showSearch = false
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
