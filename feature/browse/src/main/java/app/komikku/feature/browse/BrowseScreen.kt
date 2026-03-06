package app.komikku.feature.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BrowseScreen(
    onMangaClick: (Long) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BrowseEffect.NavigateToMangaDetail -> onMangaClick(effect.mangaId)
            }
        }
    }

    BrowseContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseContent(
    state: BrowseState,
    onEvent: (BrowseEvent) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Browse") }) },
    ) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(padding).fillMaxSize(),
        }
    }
}
