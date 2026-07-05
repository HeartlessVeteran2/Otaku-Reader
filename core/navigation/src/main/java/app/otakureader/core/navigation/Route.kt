package app.otakureader.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for Otaku Reader.
 *
 * All destinations are defined as `@Serializable` data objects/classes,
 * consumed by `androidx.navigation.compose` with type-safe navigation.
 *
 * Usage in NavHost:
 * ```kotlin
 * composable { LibraryScreen(...) }
 * composable { backStackEntry ->
 *   val args = backStackEntry.toRoute()
 *   MangaDetailsScreen(mangaId = args.mangaId)
 * }
 * ```
 *
 * Usage in navigation:
 * ```kotlin
 * navController.navigate(Route.MangaDetails(mangaId = 123L))
 * ```
 */
sealed interface Route {

    // ─── Top-level tabs ───

    @Serializable
    data object Library : Route

    @Serializable
    data object Browse : Route

    @Serializable
    data object History : Route

    @Serializable
    data object Updates : Route

    @Serializable
    data object More : Route

    /** Dedicated Update Errors screen — replaces the old in-dialog error list (see #1192). */
    @Serializable
    data object UpdateErrors : Route

    @Serializable
    data object Bookmarks : Route

    // ─── Library / Browse sub-flows ───

    /**
     * Manga details screen.
     * @param mangaId Local manga ID from the database.
     */
    @Serializable
    data class MangaDetails(val mangaId: Long) : Route

    /**
     * Reader screen.
     * @param mangaId Local manga ID.
     * @param chapterId Chapter to open.
     */
    @Serializable
    data class Reader(
        val mangaId: Long,
        val chapterId: Long,
    ) : Route

    // ─── Browse sub-flows ───

    /**
     * Source listing screen — shows manga from a single source with filters.
     * @param sourceId Installed extension source ID (string identifier).
     */
    @Serializable
    data class SourceListing(val sourceId: String, val initialQuery: String = "") : Route

    /**
     * Extension catalog — browse/install available extensions.
     */
    @Serializable
    data object ExtensionCatalog : Route

    /**
     * Extension APK install screen.
     */
    @Serializable
    data object ExtensionInstall : Route

    /**
     * Full-screen extension detail view (#1047).
     * @param packageName Android package name of the extension
     *   (e.g. "eu.kanade.tachiyomi.extension.en.mangadex").
     */
    @Serializable
    data class ExtensionDetail(val packageName: String) : Route

    /**
     * Manage custom extension repository URLs (#953).
     */
    @Serializable
    data object ExtensionRepositories : Route

    /**
     * Source manga detail — browse a manga from a source before adding to library.
     * @param sourceId Source extension ID.
     * @param mangaUrl URL slug from the source.
     * @param mangaTitle Title for display.
     */
    @Serializable
    data class SourceMangaDetail(
        val sourceId: String,
        val mangaUrl: String,
        val mangaTitle: String = "",
    ) : Route

    /**
     * Global search results across all enabled sources.
     * @param query Search string.
     */
    @Serializable
    data class Search(val query: String) : Route

    // ─── More / Settings ───

    @Serializable
    data object Settings : Route

    @Serializable
    data object SettingsAppearance : Route

    @Serializable
    data object SettingsLibrary : Route

    @Serializable
    data object SettingsReader : Route

    @Serializable
    data object SettingsDownloads : Route

    @Serializable
    data object SettingsTracking : Route

    @Serializable
    data object SettingsBackup : Route

    @Serializable
    data object SettingsCloudBackup : Route

    @Serializable
    data object SettingsDiscord : Route

    @Serializable
    data object SettingsSecurity : Route

    @Serializable
    data object SettingsNotifications : Route

    @Serializable
    data object SettingsBrowse : Route

    @Serializable
    data object WidgetConfiguration : Route

    @Serializable
    data object LocalSourceBrowser : Route

    @Serializable
    data object SettingsSync : Route

    @Serializable
    data object SettingsNavOrder : Route

    @Serializable
    data object ReaderPresets : Route

    @Serializable
    data object StorageAnalytics : Route

    // ─── Downloads ───

    @Serializable
    data object Downloads : Route

    // ─── Statistics ───

    @Serializable
    data object Statistics : Route

    // ─── Migration ───

    @Serializable
    data object MigrationEntry : Route

    @Serializable
    data class Migration(val selectedMangaIds: List<Long> = emptyList()) : Route

    // ─── Tracking ───

    @Serializable
    data class Tracking(val mangaId: Long, val mangaTitle: String) : Route

    // ─── Onboarding ───

    @Serializable
    data object Onboarding : Route

    // ─── QR Library Sharing ───

    @Serializable
    data object ShareLibrary : Route

    @Serializable
    data object ScanLibrary : Route

    // ─── About ───

    @Serializable
    data object About : Route

    @Serializable
    data object PrivacyPolicy : Route

    // ─── Feed ───

    @Serializable
    data object Feed : Route

    @Serializable
    data object FeedManagement : Route

    // ─── Category Management ───

    @Serializable
    data object CategoryManagement : Route

    // ─── Reading Lists ───

    /** User-defined reading list collections — top-level CRUD screen. */
    @Serializable
    data object ReadingLists : Route

    /**
     * A single reading list's detail/manga-grid screen.
     * @param listId The reading list's database ID.
     */
    @Serializable
    data class ReadingListDetail(val listId: Long) : Route

    // ─── OPDS (Phase 3) ───

    /**
     * OPDS catalog browser.
     * @param serverId OPDS server ID (optional — null = browse list of servers).
     */
    @Serializable
    data class OpdsCatalog(val serverId: Long? = null) : Route

    // ─── WebView Fallback ───

    /**
     * In-app WebView viewer for source pages that fail API extraction.
     * @param url Initial URL to load.
     * @param title Optional display title for the screen.
     */
    @Serializable
    data class WebViewFallback(
        val url: String,
        val title: String = "",
    ) : Route

    // ─── Data Usage ───

    @Serializable
    data object DataUsage : Route

    // ─── Merge Duplicates (#997) ───

    @Serializable
    data object MergeDuplicates : Route

    // ─── Library Maintenance (#1040) ───

    @Serializable
    data object LibraryMaintenance : Route

    // ─── Deep-link only ───

    /**
     * OAuth callback for tracker login.
     * @param tracker Tracker ID (e.g., "anilist", "mal", "kitsu").
     * @param code OAuth authorization code.
     * @param state CSRF state token returned by the provider, or null if omitted.
     */
    @Serializable
    data class TrackerOAuth(
        val tracker: String,
        val code: String,
        val state: String? = null,
    ) : Route

    // ─── WebView ───

    /**
     * Embedded WebView screen (e.g. CAPTCHA solving, OAuth).
     *
     * @param sourceId Extension source ID this WebView is serving.
     * @param url      URL to open.
     * @param purpose  [app.otakureader.core.webview.WebViewPurpose] as a String to avoid
     *                 a cross-module enum reference in the navigation layer.
     *                 Defaults to "GENERAL".
     */
    @Serializable
    data class WebView(
        val sourceId: Long,
        val url: String,
        val purpose: String = "GENERAL",
    ) : Route
}
