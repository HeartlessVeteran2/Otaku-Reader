package app.komikku.feature.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** History screen showing recently read chapters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onChapterClick: (mangaId: Long, chapterId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("History") })
        }
    ) { paddingValues ->
        // TODO: Phase 0 — reading session management feeds this screen
        Text(
            text = "Your reading history will appear here.",
            modifier = Modifier.padding(paddingValues)
        )
    }
}
