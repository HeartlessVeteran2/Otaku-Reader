package app.otakureader.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for Navigation Compose.
 * Each sealed class/object is serializable for use with Navigation Compose 2.8+.
 */
sealed interface OtakuReaderDestination

/** Top-level navigation graph destinations. */
@Serializable
object LibraryRoute : OtakuReaderDestination

@Serializable
object UpdatesRoute : OtakuReaderDestination

@Serializable
object BrowseRoute : OtakuReaderDestination

@Serializable
object HistoryRoute : OtakuReaderDestination

@Serializable
object SettingsRoute : OtakuReaderDestination

@Serializable
object DownloadsRoute : OtakuReaderDestination

@Serializable
object StatisticsRoute : OtakuReaderDestination

/** Manga detail screen. */
@Serializable
data class MangaDetailRoute(val mangaId: Long) : OtakuReaderDestination

/** Reader screen. */
@Serializable
data class ReaderRoute(val mangaId: Long, val chapterId: Long) : OtakuReaderDestination

/** Browse/Source detail screen. */
@Serializable
data class SourceDetailRoute(val sourceId: String) : OtakuReaderDestination

/** Extension management screen. */
@Serializable
object ExtensionsRoute : OtakuReaderDestination

/** Extension install screen. */
@Serializable
object ExtensionInstallRoute : OtakuReaderDestination

/** Manga detail from source (for browsing). */
@Serializable
data class SourceMangaDetailRoute(
    val sourceId: String,
    val mangaUrl: String,
    val mangaTitle: String = ""
) : OtakuReaderDestination

/** Global search across all sources. */
@Serializable
data class GlobalSearchRoute(val query: String = "") : OtakuReaderDestination
