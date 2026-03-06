package app.komikku.feature.browse

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Browse/Sources screen for discovering new manga. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onMangaClick: (sourceId: String, mangaUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Browse") })
        }
    ) { paddingValues ->
        // TODO: Phase 1 — list installed sources and allow browsing
        Text(
            text = "Browse sources will appear here.",
            modifier = Modifier.padding(paddingValues)
        )
    }
}
