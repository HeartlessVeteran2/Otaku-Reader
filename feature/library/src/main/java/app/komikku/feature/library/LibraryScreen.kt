package app.komikku.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komikku.core.ui.components.LoadingIndicator
import app.komikku.core.ui.components.MangaCard

@Composable
fun LibraryScreen(
    onMangaClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LibraryEffect.NavigateToMangaDetail -> onMangaClick(effect.mangaId)
            }
        }
    }

    LibraryContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> Text(text = state.error, modifier = Modifier.padding(16.dp))
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                items(state.manga) { manga ->
                    MangaCard(
                        title = manga.title,
                        coverUrl = manga.thumbnailUrl,
                        onClick = { onEvent(LibraryEvent.OnMangaClick(manga.id)) },
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
}
