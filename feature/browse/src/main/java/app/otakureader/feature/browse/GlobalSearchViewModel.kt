package app.otakureader.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.usecase.source.GetSourcesUseCase
import app.otakureader.domain.usecase.source.GlobalSearchUseCase
import app.otakureader.sourceapi.MangaSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val getSourcesUseCase: GetSourcesUseCase,
    private val globalSearchUseCase: GlobalSearchUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GlobalSearchState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GlobalSearchState()
    )

    private val _effect = Channel<GlobalSearchEffect>()
    val effect = _effect.receiveAsFlow()

    /** Tracks the currently active search so it can be cancelled on a new search. */
    private var searchJob: Job? = null

    fun initQuery(query: String) {
        if (query.isNotBlank() && _state.value.query.isBlank()) {
            _state.update { it.copy(query = query) }
            performSearch(query)
        }
    }

    fun onEvent(event: GlobalSearchEvent) {
        when (event) {
            is GlobalSearchEvent.OnQueryChange -> {
                _state.update { it.copy(query = event.query) }
            }
            is GlobalSearchEvent.Search -> {
                val query = _state.value.query
                if (query.isNotBlank()) {
                    performSearch(query)
                }
            }
            is GlobalSearchEvent.OnMangaClick -> {
                viewModelScope.launch {
                    _effect.send(
                        GlobalSearchEffect.NavigateToMangaDetail(event.sourceId, event.manga.url)
                    )
                }
            }
        }
    }

    private fun performSearch(query: String) {
        // Cancel any in-flight search so stale results can't overwrite the new query's results
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val sources: List<MangaSource> = getSourcesUseCase().first()

            if (sources.isEmpty()) {
                _state.update { it.copy(isSearching = false, sourceResults = emptyList()) }
                return@launch
            }

            // Initialise each source with a loading state
            _state.update {
                it.copy(
                    isSearching = true,
                    sourceResults = sources.map { source ->
                        SourceSearchResult(
                            sourceId = source.id,
                            sourceName = source.name,
                            isLoading = true
                        )
                    }
                )
            }

            // Search each source concurrently; failures are captured per-source
            sources.forEach { source ->
                launch {
                    val result = globalSearchUseCase(source.id, query)
                    _state.update { state ->
                        val updatedResults = state.sourceResults.map { sr ->
                            if (sr.sourceId == source.id) {
                                result.fold(
                                    onSuccess = { page ->
                                        sr.copy(results = page.mangas, isLoading = false)
                                    },
                                    onFailure = { error ->
                                        sr.copy(
                                            isLoading = false,
                                            error = error.message ?: "Search failed"
                                        )
                                    }
                                )
                            } else {
                                sr
                            }
                        }
                        // Mark overall search as done once all sources have responded
                        val allDone = updatedResults.none { it.isLoading }
                        state.copy(
                            isSearching = !allDone,
                            sourceResults = updatedResults
                        )
                    }
                }
            }
        }
    }
}
