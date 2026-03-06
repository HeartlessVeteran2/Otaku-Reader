package app.komikku.core.navigation

import kotlinx.serialization.Serializable

object KomikkuDestinations {

    @Serializable
    data object LibraryRoute

    @Serializable
    data object UpdatesRoute

    @Serializable
    data object BrowseRoute

    @Serializable
    data object SettingsRoute

    @Serializable
    data class MangaDetailRoute(val mangaId: Long)

    @Serializable
    data class ReaderRoute(val mangaId: Long, val chapterId: Long)
}
