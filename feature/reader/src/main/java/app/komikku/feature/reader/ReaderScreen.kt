package app.komikku.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komikku.core.ui.component.EmptyScreen
import app.komikku.core.ui.component.LoadingScreen
import coil3.compose.AsyncImage

/**
 * Reader screen supporting paged (LTR/RTL) and webtoon reading modes.
 * Reading position is persisted via [ReaderViewModel] for exact resume.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ReaderEffect.NavigateBack -> onNavigateBack()
                is ReaderEffect.ShowSnackbar -> { /* TODO: show snackbar */ }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.isMenuVisible) {
                TopAppBar(
                    title = { Text(state.chapter?.name.orEmpty()) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
            state.pages.isEmpty() -> EmptyScreen("No pages found.", Modifier.padding(paddingValues))
            else -> PagedReader(
                pages = state.pages,
                currentPage = state.currentPage,
                onPageChange = { viewModel.onEvent(ReaderEvent.OnPageChange(it)) },
                onTap = { viewModel.onEvent(ReaderEvent.ToggleMenu) },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun PagedReader(
    pages: List<String>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { pages.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onPageChange(page)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { pageIndex ->
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = pages[pageIndex],
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
