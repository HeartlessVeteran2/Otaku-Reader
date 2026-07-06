package app.otakureader.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.otakureader.core.navigation.Route
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Tracks the progress of the source-manga redirect lookup so the screen can
 * react deterministically to all outcomes instead of waiting indefinitely on
 * the effect channel.
 */
sealed class RedirectState {
    /** DB lookup is in progress — show a loading indicator. */
    object Loading : RedirectState()

    /** Lookup succeeded; navigate to the manga detail screen for [mangaId]. */
    data class Success(val mangaId: Long) : RedirectState()

    /** Lookup returned no usable ID (stub insert yielded 0). */
    object NotFound : RedirectState()

    /** An unexpected exception occurred during the lookup. */
    data class Error(val message: String) : RedirectState()
}

/**
 * Resolves a source manga (identified by [sourceId] + [mangaUrl]) to a database
 * entry so the UI can forward to the full [Route.MangaDetails] screen.
 *
 * If the manga is already in the database (previously browsed or in library) its
 * existing ID is reused. Otherwise a stub entry is inserted so the details screen
 * can load chapter/cover data on demand.
 *
 * The lookup is wrapped in a 10-second [withTimeout] so a hung DB call can never
 * leave the screen stuck in [RedirectState.Loading] indefinitely (fixes #901).
 */
@HiltViewModel
class SourceMangaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
) : ViewModel() {

    private val _redirectState = MutableStateFlow<RedirectState>(RedirectState.Loading)
    val redirectState: StateFlow<RedirectState> = _redirectState.asStateFlow()

    init {
        val route = savedStateHandle.toRoute<Route.SourceMangaDetail>()
        resolveAndNavigate(route.sourceId, route.mangaUrl, route.mangaTitle)
    }

    private fun resolveAndNavigate(sourceId: String, mangaUrl: String, mangaTitle: String) {
        viewModelScope.launch {
            try {
                val sourceIdLong = sourceId.toLongOrNull()
                if (sourceIdLong == null) {
                    _redirectState.value = RedirectState.NotFound
                    return@launch
                }

                val mangaId = withTimeout(10_000L) {
                    val existing = mangaRepository.getMangaBySourceAndUrl(sourceIdLong, mangaUrl)
                    if (existing != null) {
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
                        mangaRepository.insertManga(stub)
                    }
                }

                if (mangaId > 0L) {
                    _redirectState.value = RedirectState.Success(mangaId)
                } else {
                    _redirectState.value = RedirectState.NotFound
                }
            } catch (e: TimeoutCancellationException) {
                // The DB lookup took longer than 10 seconds — treat as not-found so the
                // screen can navigate back instead of spinning forever.
                _redirectState.value = RedirectState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _redirectState.value = RedirectState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
