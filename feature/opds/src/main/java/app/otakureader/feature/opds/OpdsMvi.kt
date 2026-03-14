package app.otakureader.feature.opds

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.OpdsEntry
import app.otakureader.domain.model.OpdsServer

/**
 * MVI state for the OPDS feature.
 */
data class OpdsState(
    val isLoading: Boolean = false,
    val servers: List<OpdsServer> = emptyList(),
    val currentServer: OpdsServer? = null,
    val feedTitle: String = "",
    val entries: List<OpdsEntry> = emptyList(),
    val navigationStack: List<String> = emptyList(),
    val searchUrl: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val error: String? = null,
    val showAddServerDialog: Boolean = false,
    val editingServer: OpdsServer? = null,
    val showDeleteConfirmation: OpdsServer? = null
) : UiState

/**
 * MVI events for the OPDS feature.
 */
sealed interface OpdsEvent : UiEvent {
    // Server management
    data object ShowAddServerDialog : OpdsEvent
    data class ShowEditServerDialog(val server: OpdsServer) : OpdsEvent
    data object DismissServerDialog : OpdsEvent
    data class SaveServer(
        val name: String,
        val url: String,
        val username: String,
        val password: String
    ) : OpdsEvent
    data class ShowDeleteConfirmation(val server: OpdsServer) : OpdsEvent
    data object DismissDeleteConfirmation : OpdsEvent
    data class ConfirmDeleteServer(val serverId: Long) : OpdsEvent

    // Catalog browsing
    data class BrowseServer(val server: OpdsServer) : OpdsEvent
    data class NavigateToFeed(val feedUrl: String) : OpdsEvent
    data object NavigateBack : OpdsEvent
    data class OnEntryClick(val entry: OpdsEntry) : OpdsEvent

    // Search
    data class OnSearchQueryChange(val query: String) : OpdsEvent
    data object PerformSearch : OpdsEvent
    data object ClearSearch : OpdsEvent

    // Favorites sync
    data class AddToFavorites(val entry: OpdsEntry) : OpdsEvent
}

/**
 * MVI side effects for the OPDS feature.
 */
sealed interface OpdsEffect : UiEffect {
    data class ShowSnackbar(val message: String) : OpdsEffect
    data class NavigateToMangaDetail(val mangaId: Long) : OpdsEffect
}
