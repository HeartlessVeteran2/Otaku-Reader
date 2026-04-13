package app.otakureader.feature.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Transparent redirect screen for [app.otakureader.core.navigation.SourceMangaDetailRoute].
 *
 * Shows a loading spinner while [SourceMangaDetailViewModel] resolves the manga's
 * database ID (looking it up by source URL, or inserting a stub entry), then
 * immediately forwards to the [MangaDetailRoute] screen via [onNavigateToMangaDetail].
 */
@Composable
fun SourceMangaDetailScreen(
    onNavigateToMangaDetail: (mangaId: Long) -> Unit,
    viewModel: SourceMangaDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SourceMangaDetailEffect.NavigateToMangaDetail -> {
                    onNavigateToMangaDetail(effect.mangaId)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
