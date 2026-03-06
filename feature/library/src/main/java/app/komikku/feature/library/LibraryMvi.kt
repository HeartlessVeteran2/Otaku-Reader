package app.komikku.feature.library

import app.komikku.core.common.mvi.UiEffect
import app.komikku.core.common.mvi.UiEvent
import app.komikku.core.common.mvi.UiState
import app.komikku.domain.model.Category
import app.komikku.domain.model.LibraryManga

// ===== MVI State =====

/**
 * UI state for the library screen following the MVI pattern.
 */
data class LibraryState(
    val isLoading: Boolean = false,
    val manga: List<LibraryManga> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedManga: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val activeCategory: Long = 0L,
    val error: String? = null
) : UiState

// ===== MVI Events (user actions) =====

sealed interface LibraryEvent : UiEvent {
    data object Refresh : LibraryEvent
    data class OnMangaClick(val mangaId: Long) : LibraryEvent
    data class OnMangaLongClick(val mangaId: Long) : LibraryEvent
    data class OnSearchQueryChange(val query: String) : LibraryEvent
    data class OnCategoryChange(val categoryId: Long) : LibraryEvent
    data object ClearSelection : LibraryEvent
    data class RemoveFromLibrary(val mangaIds: Set<Long>) : LibraryEvent
}

// ===== MVI Effects (one-shot side effects) =====

sealed interface LibraryEffect : UiEffect {
    data class NavigateToManga(val mangaId: Long) : LibraryEffect
    data class ShowSnackbar(val message: String) : LibraryEffect
}
