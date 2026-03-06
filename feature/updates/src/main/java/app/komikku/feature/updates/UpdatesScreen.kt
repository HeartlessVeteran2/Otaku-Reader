package app.komikku.feature.updates

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Updates screen showing newly released chapters for library manga. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onChapterClick: (mangaId: Long, chapterId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Updates") })
        }
    ) { paddingValues ->
        // TODO: Phase 0 — smart notification system feeds this screen
        Text(
            text = "Chapter updates will appear here.",
            modifier = Modifier.padding(paddingValues)
        )
    }
}
