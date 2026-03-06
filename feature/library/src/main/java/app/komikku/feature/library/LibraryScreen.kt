package app.komikku.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komikku.core.ui.component.EmptyScreen
import app.komikku.core.ui.component.LoadingScreen
import app.komikku.domain.model.LibraryManga
import coil3.compose.AsyncImage

/**
 * Entry point composable for the Library feature.
 * Stateless: state comes from [LibraryViewModel] and is hoisted via parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onMangaClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LibraryEffect.NavigateToManga -> onMangaClick(effect.mangaId)
                is LibraryEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { /* TODO: open search */ }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search library")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            state.manga.isEmpty() -> EmptyScreen(
                message = "Your library is empty.\nBrowse sources to find manga.",
                modifier = Modifier.padding(paddingValues)
            )
            else -> LibraryGrid(
                mangaList = state.manga,
                onMangaClick = { viewModel.onEvent(LibraryEvent.OnMangaClick(it)) },
                onMangaLongClick = { viewModel.onEvent(LibraryEvent.OnMangaLongClick(it)) },
                selectedManga = state.selectedManga,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGrid(
    mangaList: List<LibraryManga>,
    onMangaClick: (Long) -> Unit,
    onMangaLongClick: (Long) -> Unit,
    selectedManga: Set<Long>,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(mangaList, key = { it.manga.id }) { libraryManga ->
            MangaGridItem(
                manga = libraryManga,
                isSelected = libraryManga.manga.id in selectedManga,
                onClick = { onMangaClick(libraryManga.manga.id) },
                onLongClick = { onMangaLongClick(libraryManga.manga.id) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaGridItem(
    manga: LibraryManga,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = manga.manga.thumbnailUrl,
            contentDescription = manga.manga.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (manga.unreadCount > 0) {
            Badge(
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = manga.unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
