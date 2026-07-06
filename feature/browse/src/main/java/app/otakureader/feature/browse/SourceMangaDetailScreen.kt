package app.otakureader.feature.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Transparent redirect screen for [Route.SourceMangaDetail].
 *
 * Shows a loading spinner while [SourceMangaDetailViewModel] resolves the manga's
 * database ID (looking it up by source URL, or inserting a stub entry), then
 * immediately forwards to the [Route.MangaDetails] screen via [onNavigateToMangaDetail].
 *
 * If the lookup fails or times out the screen calls [onNavigateBack] so the user
 * is never left on an infinite loading spinner (fixes #901).
 */
@Composable
fun SourceMangaDetailScreen(
    onNavigateToMangaDetail: (mangaId: Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SourceMangaDetailViewModel = hiltViewModel(),
) {
    val redirectState by viewModel.redirectState.collectAsStateWithLifecycle()

    LaunchedEffect(redirectState) {
        when (val state = redirectState) {
            is RedirectState.Success -> onNavigateToMangaDetail(state.mangaId)
            is RedirectState.NotFound -> onNavigateBack()
            is RedirectState.Error -> onNavigateBack()
            RedirectState.Loading -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
