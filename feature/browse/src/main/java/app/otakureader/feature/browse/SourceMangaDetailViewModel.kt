package app.otakureader.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.otakureader.core.navigation.SourceMangaDetailRoute
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SourceMangaDetailEffect {
    data class NavigateToMangaDetail(val mangaId: Long) : SourceMangaDetailEffect
}

/**
 * Resolves a source manga (identified by [sourceId] + [mangaUrl]) to a database
 * entry and emits [SourceMangaDetailEffect.NavigateToMangaDetail] so the UI can
 * forward to the full [MangaDetailRoute] screen.
 *
 * If the manga is already in the database (previously browsed or in library) its
 * existing ID is reused. Otherwise a stub entry is inserted so the details screen
 * can load chapter/cover data on demand.
 */
@HiltViewModel
class SourceMangaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
) : ViewModel() {

    private val _effect = Channel<SourceMangaDetailEffect>()
    val effect: Flow<SourceMangaDetailEffect> = _effect.receiveAsFlow()

    init {
        val route = savedStateHandle.toRoute<SourceMangaDetailRoute>()
        resolveAndNavigate(route.sourceId, route.mangaUrl, route.mangaTitle)
    }

    private fun resolveAndNavigate(sourceId: String, mangaUrl: String, mangaTitle: String) {
        viewModelScope.launch {
            val sourceIdLong = sourceId.toLongOrNull() ?: 0L
            val existing = runCatching {
                mangaRepository.getMangaBySourceAndUrl(sourceIdLong, mangaUrl)
            }.getOrNull()

            val mangaId = if (existing != null) {
                existing.id
            } else {
                // Insert a lightweight stub so DetailsScreen can load the full info.
                val stub = Manga(
                    id = 0,
                    sourceId = sourceIdLong,
                    url = mangaUrl,
                    title = mangaTitle.ifBlank { mangaUrl },
                    initialized = false
                )
                runCatching { mangaRepository.insertManga(stub) }.getOrDefault(0L)
            }

            if (mangaId > 0L) {
                _effect.send(SourceMangaDetailEffect.NavigateToMangaDetail(mangaId))
            }
        }
    }
}
