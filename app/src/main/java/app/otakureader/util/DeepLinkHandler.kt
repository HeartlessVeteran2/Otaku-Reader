package app.otakureader.util

import android.content.Intent
import android.net.Uri

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
    
    object Invalid : DeepLinkResult()
}

/**
 * Utility class for parsing deep links and share intents.
 */
object DeepLinkHandler {
    
    /**
     * Parse an intent to extract deep link information.
     */
    fun parseIntent(intent: Intent?): DeepLinkResult {
        if (intent == null) return DeepLinkResult.Invalid
        
        return when (intent.action) {
            Intent.ACTION_VIEW -> parseViewIntent(intent)
            Intent.ACTION_SEND -> parseSendIntent(intent)
            else -> DeepLinkResult.Invalid
        }
    }
    
    /**
     * Parse a VIEW intent (deep link from browser, Discord, etc.)
     */
    private fun parseViewIntent(intent: Intent): DeepLinkResult {
        val data: Uri = intent.data ?: return DeepLinkResult.Invalid
        val host = data.host?.lowercase() ?: return DeepLinkResult.Invalid
        
        // Handle MangaDex URLs - allow the main domain and its subdomains
        if (host == "mangadex.org" || host.endsWith(".mangadex.org")) {
            return parseMangaDexUrl(data)
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
     * Parse MangaDex-specific URLs
     */
    private fun parseMangaDexUrl(uri: Uri): DeepLinkResult {
        val pathSegments = uri.pathSegments
        
        if (pathSegments.size >= 2 && pathSegments[0] == "title") {
            val mangaId = pathSegments[1]
            return DeepLinkResult.MangaUrl(
                baseUrl = "https://mangadex.org",
                mangaUrl = "https://mangadex.org/title/$mangaId",
                title = null
            )
        }
        
        return DeepLinkResult.Invalid
    }
    
    /**
     * Parse generic manga URLs from various sources
     */
    private fun parseGenericMangaUrl(uri: Uri, host: String): DeepLinkResult {
        return when {
            // Use exact host matching or strict suffix checks for security
            host == "mangakakalot.com" || host.endsWith(".mangakakalot.com") ||
            host == "manganato.com" || host.endsWith(".manganato.com") ||
            host == "manganelo.com" || host.endsWith(".manganelo.com") -> {
                DeepLinkResult.MangaUrl(
                    baseUrl = "${uri.scheme ?: "https"}://$host",
                    mangaUrl = uri.toString(),
                    title = null
                )
            }
            
            host == "webtoons.com" || host.endsWith(".webtoons.com") -> {
                DeepLinkResult.MangaUrl(
                    baseUrl = "${uri.scheme ?: "https"}://$host",
                    mangaUrl = uri.toString(),
                    title = null
                )
            }
            
            else -> DeepLinkResult.Invalid
        }
    }
    
    /**
     * Check if the given URL is a supported manga URL
     */
    fun isSupportedUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return false
        
        return when {
            host == "mangadex.org" || host.endsWith(".mangadex.org") -> true
            host == "mangakakalot.com" || host.endsWith(".mangakakalot.com") -> true
            host == "manganato.com" || host.endsWith(".manganato.com") -> true
            host == "manganelo.com" || host.endsWith(".manganelo.com") -> true
            host == "webtoons.com" || host.endsWith(".webtoons.com") -> true
            else -> false
        }
    }
}