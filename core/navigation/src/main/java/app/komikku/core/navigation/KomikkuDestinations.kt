package app.komikku.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for Navigation Compose.
 * Each sealed class/object is serializable for use with Navigation Compose 2.8+.
 */
sealed interface KomikkuDestination

/** Top-level navigation graph destinations. */
@Serializable
object LibraryRoute : KomikkuDestination

@Serializable
object UpdatesRoute : KomikkuDestination

@Serializable
object BrowseRoute : KomikkuDestination

@Serializable
object HistoryRoute : KomikkuDestination

@Serializable
object SettingsRoute : KomikkuDestination

/** Manga detail screen. */
@Serializable
data class MangaDetailRoute(val mangaId: Long) : KomikkuDestination

/** Reader screen. */
@Serializable
data class ReaderRoute(val mangaId: Long, val chapterId: Long) : KomikkuDestination

/** Browse/Source detail screen. */
@Serializable
data class SourceDetailRoute(val sourceId: String) : KomikkuDestination
