package app.komikku.feature.updates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UpdatesScreen(
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UpdatesContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesContent(
    state: UpdatesState,
    onEvent: (UpdatesEvent) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Updates") }) },
    ) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(padding).fillMaxSize(),
        }
    }
}
