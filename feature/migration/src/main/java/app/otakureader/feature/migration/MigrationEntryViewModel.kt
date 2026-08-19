package app.otakureader.feature.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.associateBySourceKey
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MigrationEntryViewModel @Inject constructor(
    private val getLibraryManga: GetLibraryMangaUseCase,
    private val sourceRepository: SourceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MigrationEntryState())
    val state: StateFlow<MigrationEntryState> = _state.asStateFlow()

    private val _effect = Channel<MigrationEntryEffect>(Channel.BUFFERED)
    val effect: Flow<MigrationEntryEffect> = _effect.receiveAsFlow()

    /** Tracks the active library collection so Retry cancels the prior one instead of stacking. */
    private var loadJob: Job? = null

    init {
        loadLibrary()
    }

    fun onEvent(event: MigrationEntryEvent) {
        when (event) {
            is MigrationEntryEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is MigrationEntryEvent.OnMangaToggle -> toggleManga(event.mangaId)
            MigrationEntryEvent.SelectAll -> selectAll()
            MigrationEntryEvent.SelectAllStranded -> selectAllStranded()
            MigrationEntryEvent.ToggleStrandedFilter -> toggleStrandedFilter()
            MigrationEntryEvent.ClearSelection -> clearSelection()
            MigrationEntryEvent.OnStartMigration -> startMigration()
            MigrationEntryEvent.NavigateBack -> navigateBack()
            MigrationEntryEvent.Retry -> loadLibrary()
        }
    }

    private fun loadLibrary() {
        // Cancel any in-flight collection first: loadLibrary() runs in init and again on every
        // Retry, and each launchIn started a new collector. Without cancelling, N retries left N
        // concurrent collectors racing to update _state. Hold the Job and cancel before relaunch.
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        // Combined rather than read once, because both sides change while this screen is open:
        // the library updates as entries are migrated, and the source list is populated
        // asynchronously at startup and again after a refresh. Resolving against a snapshot taken
        // before sources finished loading would mark the entire library as stranded.
        loadJob = combine(getLibraryManga(), sourceRepository.getSources()) { manga, sources ->
            // The one correct key -> source bridge. `associateBySourceKey` is the same index
            // `getSourceByKey` uses, legacy-key precedence included; matching on
            // `sourceId.toString()` compares a hash's decimal against a real id and never hits.
            val byKey = sources.associateBySourceKey { it.id }
            manga.map { m ->
                MigrationEntryItem(
                    id = m.id,
                    title = m.title,
                    thumbnailUrl = m.thumbnailUrl,
                    sourceName = byKey[m.sourceId]?.name
                )
            }
        }
            .onEach { items ->
                _state.update { state ->
                    state.copy(isLoading = false, error = null, mangaList = items)
                }
            }
            .catch { e ->
                val message = e.message ?: "Failed to load library"
                _state.update { it.copy(isLoading = false, error = message) }
                _effect.send(MigrationEntryEffect.ShowError(message))
            }
            .launchIn(viewModelScope)
    }

    private fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    private fun toggleManga(mangaId: Long) {
        _state.update { state ->
            val newSelection = if (mangaId in state.selectedIds) {
                state.selectedIds - mangaId
            } else {
                state.selectedIds + mangaId
            }
            state.copy(selectedIds = newSelection)
        }
    }

    private fun selectAll() {
        _state.update { state ->
            val allIds = filteredList(state).map { it.id }.toSet()
            state.copy(selectedIds = allIds)
        }
    }

    /**
     * Select exactly the entries no loaded source can serve.
     *
     * Deliberately ignores the search query and the stranded filter, unlike [selectAll]. This is
     * the "fix what is broken" action, and its value is that the user does not have to have found
     * the broken entries first — narrowing it by whatever happens to be typed in the search box
     * would silently select a subset and leave the rest behind.
     */
    private fun selectAllStranded() {
        _state.update { state ->
            state.copy(selectedIds = state.mangaList.filter { it.isStranded }.map { it.id }.toSet())
        }
    }

    private fun toggleStrandedFilter() {
        _state.update { it.copy(showOnlyStranded = !it.showOnlyStranded) }
    }

    private fun clearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    private fun startMigration() {
        val selected = _state.value.selectedIds.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _effect.send(MigrationEntryEffect.NavigateToMigration(selected))
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _effect.send(MigrationEntryEffect.NavigateBack)
        }
    }

    /** The manga list as the screen shows it: narrowed by the stranded filter, then the query. */
    fun filteredList(state: MigrationEntryState = _state.value): List<MigrationEntryItem> {
        val query = state.searchQuery.trim()
        val bySource = if (state.showOnlyStranded) state.mangaList.filter { it.isStranded } else state.mangaList
        return if (query.isBlank()) bySource
        else bySource.filter { it.title.contains(query, ignoreCase = true) }
    }
}
