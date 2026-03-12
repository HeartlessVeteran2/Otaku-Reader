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

/** Mass migration screen. */
@Serializable
data class MigrationRoute(val selectedMangaIds: List<Long> = emptyList()) : OtakuReaderDestination

/** Migration entry screen – select library manga to migrate. */
@Serializable
object MigrationEntryRoute : OtakuReaderDestination

/** Tracking screen for a specific manga. */
@Serializable
data class TrackingRoute(val mangaId: Long, val mangaTitle: String) : OtakuReaderDestination

/** Onboarding screen for first-time users. */
@Serializable
object OnboardingRoute : OtakuReaderDestination

/** About screen with help, FAQ, and app information. */
@Serializable
object AboutRoute : OtakuReaderDestination

/** OPDS catalog browser screen. */
@Serializable
object OpdsRoute : OtakuReaderDestination
