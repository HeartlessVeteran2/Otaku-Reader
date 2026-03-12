package app.otakureader.feature.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.model.OpdsEntry
import app.otakureader.domain.model.OpdsServer
import app.otakureader.domain.usecase.opds.BrowseOpdsCatalogUseCase
import app.otakureader.domain.usecase.opds.DeleteOpdsServerUseCase
import app.otakureader.domain.usecase.opds.GetOpdsServersUseCase
import app.otakureader.domain.usecase.opds.SaveOpdsServerUseCase
import app.otakureader.domain.usecase.opds.SearchOpdsCatalogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OpdsViewModel @Inject constructor(
    private val getOpdsServers: GetOpdsServersUseCase,
    private val saveOpdsServer: SaveOpdsServerUseCase,
    private val deleteOpdsServer: DeleteOpdsServerUseCase,
    private val browseOpdsCatalog: BrowseOpdsCatalogUseCase,
    private val searchOpdsCatalog: SearchOpdsCatalogUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(OpdsState())
    val state: StateFlow<OpdsState> = _state.asStateFlow()

    private val _effect = Channel<OpdsEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        observeServers()
    }

    fun onEvent(event: OpdsEvent) {
        when (event) {
            is OpdsEvent.ShowAddServerDialog -> {
                _state.update { it.copy(showAddServerDialog = true, editingServer = null) }
            }
            is OpdsEvent.ShowEditServerDialog -> {
                _state.update { it.copy(showAddServerDialog = true, editingServer = event.server) }
            }
            is OpdsEvent.DismissServerDialog -> {
                _state.update { it.copy(showAddServerDialog = false, editingServer = null) }
            }
            is OpdsEvent.SaveServer -> saveServer(event)
            is OpdsEvent.ShowDeleteConfirmation -> {
                _state.update { it.copy(showDeleteConfirmation = event.server) }
            }
            is OpdsEvent.DismissDeleteConfirmation -> {
                _state.update { it.copy(showDeleteConfirmation = null) }
            }
            is OpdsEvent.ConfirmDeleteServer -> deleteServer(event.serverId)
            is OpdsEvent.BrowseServer -> browseServer(event.server)
            is OpdsEvent.NavigateToFeed -> navigateToFeed(event.feedUrl)
            is OpdsEvent.NavigateBack -> navigateBack()
            is OpdsEvent.OnEntryClick -> onEntryClick(event.entry)
            is OpdsEvent.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = event.query) }
            }
            is OpdsEvent.PerformSearch -> performSearch()
            is OpdsEvent.ClearSearch -> clearSearch()
            is OpdsEvent.AddToFavorites -> addToFavorites(event.entry)
        }
    }

    private fun observeServers() {
        getOpdsServers()
            .onEach { servers ->
                _state.update { it.copy(servers = servers) }
            }
            .launchIn(viewModelScope)
    }

    private fun saveServer(event: OpdsEvent.SaveServer) {
        viewModelScope.launch {
            try {
                val server = _state.value.editingServer?.copy(
                    name = event.name,
                    url = event.url,
                    username = event.username,
                    password = event.password
                ) ?: OpdsServer(
                    name = event.name,
                    url = event.url,
                    username = event.username,
                    password = event.password
                )
                saveOpdsServer(server)
                _state.update { it.copy(showAddServerDialog = false, editingServer = null) }
                _effect.send(OpdsEffect.ShowSnackbar("Server saved"))
            } catch (e: Exception) {
                _effect.send(OpdsEffect.ShowSnackbar(e.message ?: "Failed to save server"))
            }
        }
    }

    private fun deleteServer(serverId: Long) {
        viewModelScope.launch {
            deleteOpdsServer(serverId)
            _state.update { it.copy(showDeleteConfirmation = null) }
            if (_state.value.currentServer?.id == serverId) {
                _state.update {
                    it.copy(
                        currentServer = null,
                        entries = emptyList(),
                        navigationStack = emptyList(),
                        feedTitle = ""
                    )
                }
            }
            _effect.send(OpdsEffect.ShowSnackbar("Server deleted"))
        }
    }

    private fun browseServer(server: OpdsServer) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    currentServer = server,
                    navigationStack = listOf(server.url),
                    error = null,
                    searchQuery = "",
                    isSearching = false
                )
            }
            browseOpdsCatalog(server, server.url)
                .onSuccess { feed ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            feedTitle = feed.title,
                            entries = feed.entries,
                            searchUrl = feed.searchUrl,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error"
                        )
                    }
                }
        }
    }

    private fun navigateToFeed(feedUrl: String) {
        val server = _state.value.currentServer ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    navigationStack = it.navigationStack + feedUrl,
                    error = null,
                    isSearching = false,
                    searchQuery = ""
                )
            }
            browseOpdsCatalog(server, feedUrl)
                .onSuccess { feed ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            feedTitle = feed.title,
                            entries = feed.entries,
                            searchUrl = feed.searchUrl ?: it.searchUrl,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error",
                            navigationStack = it.navigationStack.dropLast(1)
                        )
                    }
                }
        }
    }

    private fun navigateBack() {
        val stack = _state.value.navigationStack
        if (stack.size <= 1) {
            _state.update {
                it.copy(
                    currentServer = null,
                    entries = emptyList(),
                    navigationStack = emptyList(),
                    feedTitle = "",
                    searchUrl = null,
                    searchQuery = "",
                    isSearching = false,
                    error = null
                )
            }
            return
        }

        val previousUrl = stack[stack.size - 2]
        val server = _state.value.currentServer ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    navigationStack = stack.dropLast(1),
                    error = null,
                    isSearching = false,
                    searchQuery = ""
                )
            }
            browseOpdsCatalog(server, previousUrl)
                .onSuccess { feed ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            feedTitle = feed.title,
                            entries = feed.entries,
                            searchUrl = feed.searchUrl ?: it.searchUrl,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    private fun onEntryClick(entry: OpdsEntry) {
        val navLink = entry.links.firstOrNull { it.isNavigation }
        if (navLink != null) {
            navigateToFeed(navLink.href)
        }
    }

    private fun performSearch() {
        val query = _state.value.searchQuery
        val searchUrl = _state.value.searchUrl
        val server = _state.value.currentServer

        if (query.isBlank() || searchUrl == null || server == null) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isSearching = true, error = null) }
            searchOpdsCatalog(server, searchUrl, query)
                .onSuccess { feed ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            entries = feed.entries,
                            feedTitle = feed.title.ifBlank { "Search: $query" },
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Search failed"
                        )
                    }
                }
        }
    }

    private fun clearSearch() {
        val server = _state.value.currentServer ?: return
        val currentUrl = _state.value.navigationStack.lastOrNull() ?: return
        _state.update { it.copy(searchQuery = "", isSearching = false) }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            browseOpdsCatalog(server, currentUrl)
                .onSuccess { feed ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            feedTitle = feed.title,
                            entries = feed.entries,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    private fun addToFavorites(entry: OpdsEntry) {
        viewModelScope.launch {
            _effect.send(OpdsEffect.ShowSnackbar("\"${entry.title}\" noted for favorites"))
        }
    }
}
