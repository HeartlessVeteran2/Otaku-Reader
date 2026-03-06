package app.komikku.feature.browse

import app.komikku.domain.manga.model.Manga

data class BrowseState(
    val isLoading: Boolean = false,
    val manga: List<Manga> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
)

sealed class BrowseEvent {
    data class OnMangaClick(val mangaId: Long) : BrowseEvent()
    data class OnSearchQueryChange(val query: String) : BrowseEvent()
    data object OnSearch : BrowseEvent()
}

sealed class BrowseEffect {
    data class NavigateToMangaDetail(val mangaId: Long) : BrowseEffect()
}
