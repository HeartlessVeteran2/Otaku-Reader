package app.otakureader.util

import android.content.Intent
import android.net.Uri
import app.otakureader.shortcut.AppShortcutManager
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Sealed class representing a parsed deep link or share intent result.
 */
sealed class DeepLinkResult {
    data class MangaUrl(
        /** The base URL of the source (e.g. "https://mangadex.org"). Use DeepLinkViewModel
         *  to resolve this to an installed numeric source ID at runtime. */
        val baseUrl: String,
        val mangaUrl: String,
        val title: String? = null
    ) : DeepLinkResult()

    data class SearchQuery(
        val query: String
    ) : DeepLinkResult()

    /** OAuth callback for tracker login. */
    data class TrackerOAuth(
        val tracker: String,
        val code: String,
        /** CSRF state token returned by the provider; null if the provider omitted it. */
        val state: String? = null,
    ) : DeepLinkResult()

    /** Navigate directly to the Library screen. */
    object NavigateToLibrary : DeepLinkResult()

    /** Navigate directly to the Updates screen. */
    object NavigateToUpdates : DeepLinkResult()

    /** Continue reading the last-read manga chapter. */
    data class ContinueReading(
        val mangaId: Long,
        val chapterId: Long
    ) : DeepLinkResult()

    /** Navigate directly to manga details (e.g. from a widget tap with no chapter context). */
    data class NavigateToManga(val mangaId: Long) : DeepLinkResult()

    object Invalid : DeepLinkResult()
}

/**
 * Utility class for parsing deep links and share intents.
 */
object DeepLinkHandler {

    private val UUID_REGEX =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    private val NUMERIC_ID_REGEX = Regex("^\\d+$")

    const val EXTRA_MANGA_ID = "manga_id"
    const val EXTRA_CHAPTER_ID = "chapter_id"

    /**
     * Parse an intent to extract deep link information.
     */
    fun parseIntent(intent: Intent?): DeepLinkResult {
        if (intent == null) return DeepLinkResult.Invalid

        // Widget navigation extras take priority — Glance passes these when a widget item is tapped.
        if (intent.hasExtra(EXTRA_MANGA_ID)) {
            val mangaId = intent.getLongExtra(EXTRA_MANGA_ID, -1L)
            val chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, -1L)
            return when {
                mangaId != -1L && chapterId != -1L -> DeepLinkResult.ContinueReading(mangaId, chapterId)
                mangaId != -1L -> DeepLinkResult.NavigateToManga(mangaId)
                else -> DeepLinkResult.Invalid
            }
        }

        return when (intent.action) {
            Intent.ACTION_VIEW -> parseViewIntent(intent)
            Intent.ACTION_SEND -> parseSendIntent(intent)
            AppShortcutManager.ACTION_SHORTCUT_LIBRARY -> DeepLinkResult.NavigateToLibrary
            AppShortcutManager.ACTION_SHORTCUT_UPDATES -> DeepLinkResult.NavigateToUpdates
            AppShortcutManager.ACTION_SHORTCUT_CONTINUE_READING -> parseContinueReadingIntent(intent)
            else -> DeepLinkResult.Invalid
        }
    }

    /**
     * Parse a VIEW intent (deep link from browser, Discord, etc.)
     */
    private fun parseViewIntent(intent: Intent): DeepLinkResult {
        val data: Uri = intent.data ?: return DeepLinkResult.Invalid
        val scheme = data.scheme?.lowercase() ?: return DeepLinkResult.Invalid
        val host = data.host?.lowercase() ?: return DeepLinkResult.Invalid

        // Handle OAuth callbacks first (custom scheme)
        if (scheme == "app.otakureader") {
            return parseOAuthCallback(data, host)
        }

        // Handle MangaDex URLs - allow the main domain and its subdomains
        if (host == "mangadex.org" || host.endsWith(".mangadex.org")) {
            return parseMangaDexUrl(data)
        }

        // Handle MangaPlus URLs separately (numeric ID validation)
        if (host == "mangaplus.shueisha.co.jp") {
            return parseMangaPlusUrl(data)
        }

        // Handle generic manga URLs
        return parseGenericMangaUrl(data, host)
    }

    /**
     * Parse a SEND intent (share from other apps)
     */
    private fun parseSendIntent(intent: Intent): DeepLinkResult {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return DeepLinkResult.Invalid

        // Check if shared text contains one or more URLs
        val urlRegex = "https?://[^\\s]+".toRegex()
        val urlMatches = urlRegex.findAll(sharedText)

        // Try each URL match until one yields a valid deep link result
        for (urlMatch in urlMatches) {
            // Remove common trailing punctuation from URLs (e.g., ")", ".", ",")
            val rawUrl = urlMatch.value
            val cleanUrl = rawUrl.trimEnd('.', ',', ')', ']', '}', '!', '?', ';', ':', '\'', '"')
            val url = Uri.parse(cleanUrl)
            val result = parseViewIntent(Intent(Intent.ACTION_VIEW, url))
            if (result !is DeepLinkResult.Invalid) {
                return result
            }
        }

        // If no supported URL was found, treat the entire shared text as a search query (already trimmed)
        return DeepLinkResult.SearchQuery(sharedText)
    }

    /**
     * Parse OAuth callback URLs from tracker services.
     *
     * Supported hosts:
     * - `kitsu-oauth` → Kitsu
     * - `mal-oauth` → MyAnimeList
     * - `shikimori-oauth` → Shikimori
     * - `anilist-oauth` → AniList
     *
     * Two shapes arrive here, because the trackers do not use the same grant.
     *
     * Kitsu, MAL and Shikimori use the authorization-code flow: the value comes back as a
     * `code` **query parameter**, and the app exchanges it for a token.
     *
     * AniList uses the implicit grant, so the token itself comes back in the URL **fragment**
     * (`#access_token=…`). A fragment is not a query string — `getQueryParameter` cannot see it,
     * so reading only `code` here would have discarded every AniList login while the redirect
     * itself arrived perfectly well. AniList's code flow is not an option: it requires a client
     * secret, and a secret shipped inside an APK is not a secret.
     */
    private fun parseOAuthCallback(uri: Uri, host: String): DeepLinkResult {
        val tracker = when (host) {
            "kitsu-oauth" -> "kitsu"
            "mal-oauth" -> "mal"
            "shikimori-oauth" -> "shikimori"
            "anilist-oauth" -> "anilist"
            else -> return DeepLinkResult.Invalid
        }
        val code = uri.getQueryParameter("code")
            ?: uri.fragmentParameter("access_token")
            ?: return DeepLinkResult.Invalid
        // The state comes back wherever the grant put it. Reading only the query would have found
        // nothing for AniList — the implicit grant returns `state` in the *fragment*, next to the
        // token — and a null state is what the callback treats as "provider omitted it", which
        // skips the CSRF comparison entirely. So the one flow that most needs the check is exactly
        // the one that would have silently gone without it.
        val state = uri.getQueryParameter("state") ?: uri.fragmentParameter("state")
        return DeepLinkResult.TrackerOAuth(tracker = tracker, code = code, state = state)
    }

    /**
     * Read a parameter out of the URL fragment.
     *
     * `Uri` offers no accessor for this: the fragment is opaque to it, so the
     * `key=value&key=value` body an implicit grant puts there has to be split by hand — including
     * the percent-decoding `getQueryParameter` would have done. `URLDecoder` matches the
     * `URLEncoder` the authorization URL is built with, so a state round-trips to the exact bytes
     * that were stored before the browser opened.
     */
    private fun Uri.fragmentParameter(name: String): String? =
        fragment
            ?.split('&')
            ?.firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrNull() }

    /**
     * Parse MangaDex-specific URLs.
     * Validates that the ID is a well-formed UUID to prevent deep link hijacking.
     * Handles both /title/{uuid} and /chapter/{uuid} paths.
     */
    private fun parseMangaDexUrl(uri: Uri): DeepLinkResult {
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 2) return DeepLinkResult.Invalid

        val id = pathSegments[1]
        if (!UUID_REGEX.matches(id)) return DeepLinkResult.Invalid

        return when (pathSegments[0]) {
            "title" -> DeepLinkResult.MangaUrl(
                baseUrl = "https://mangadex.org",
                mangaUrl = "https://mangadex.org/title/$id",
                title = null
            )
            "chapter" -> DeepLinkResult.MangaUrl(
                baseUrl = "https://mangadex.org",
                mangaUrl = "https://mangadex.org/chapter/$id",
                title = null
            )
            else -> DeepLinkResult.Invalid
        }
    }

    /**
     * Parse MangaPlus URLs. Validates that the title ID is numeric to prevent hijacking.
     * Expected path: /titles/{numericId}
     */
    private fun parseMangaPlusUrl(uri: Uri): DeepLinkResult {
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 2 || pathSegments[0] != "titles") return DeepLinkResult.Invalid
        val titleId = pathSegments[1]
        if (!NUMERIC_ID_REGEX.matches(titleId)) return DeepLinkResult.Invalid
        return DeepLinkResult.MangaUrl(
            baseUrl = "https://mangaplus.shueisha.co.jp",
            mangaUrl = uri.toString(),
            title = null
        )
    }

    /**
     * Parse generic manga URLs from various sources.
     * Uses exact host or strict suffix matching to prevent subdomain hijacking.
     */
    private fun parseGenericMangaUrl(uri: Uri, host: String): DeepLinkResult {
        val scheme = uri.scheme ?: "https"
        val baseUrl = "$scheme://$host"
        val pathSegments = uri.pathSegments
        return when {
            host == "mangakakalot.com" || host.endsWith(".mangakakalot.com") ||
            host == "manganato.com" || host.endsWith(".manganato.com") ||
            host == "manganelo.com" || host.endsWith(".manganelo.com") ->
                DeepLinkResult.MangaUrl(baseUrl = baseUrl, mangaUrl = uri.toString())

            host == "webtoons.com" || host.endsWith(".webtoons.com") ->
                DeepLinkResult.MangaUrl(baseUrl = baseUrl, mangaUrl = uri.toString())

            host == "mangasee123.com" || host == "mangafire.to" -> {
                // Require /manga/{slug} path
                if (pathSegments.size < 2 || pathSegments[0] != "manga") return DeepLinkResult.Invalid
                DeepLinkResult.MangaUrl(baseUrl = baseUrl, mangaUrl = uri.toString())
            }

            host == "bato.to" -> {
                // Require /title/{id} or /series/{id} path
                if (pathSegments.size < 2 || pathSegments[0] !in setOf("title", "series"))
                    return DeepLinkResult.Invalid
                DeepLinkResult.MangaUrl(baseUrl = baseUrl, mangaUrl = uri.toString())
            }

            else -> DeepLinkResult.Invalid
        }
    }

    /**
     * Parse a "Continue Reading" shortcut intent to extract manga/chapter IDs.
     */
    private fun parseContinueReadingIntent(intent: Intent): DeepLinkResult {
        val mangaId = intent.getLongExtra(AppShortcutManager.EXTRA_MANGA_ID, -1L)
        val chapterId = intent.getLongExtra(AppShortcutManager.EXTRA_CHAPTER_ID, -1L)
        return if (mangaId != -1L && chapterId != -1L) {
            DeepLinkResult.ContinueReading(mangaId, chapterId)
        } else {
            DeepLinkResult.Invalid
        }
    }
}
