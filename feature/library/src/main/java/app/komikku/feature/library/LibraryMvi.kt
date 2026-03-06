package app.komikku.feature.library

import app.komikku.domain.manga.model.Manga

data class LibraryState(
    val isLoading: Boolean = false,
    val manga: List<Manga> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
)

sealed class LibraryEvent {
    data class OnMangaClick(val mangaId: Long) : LibraryEvent()
    data class OnSearchQueryChange(val query: String) : LibraryEvent()
    data object OnRefresh : LibraryEvent()
}

sealed class LibraryEffect {
    data class NavigateToMangaDetail(val mangaId: Long) : LibraryEffect()
}
