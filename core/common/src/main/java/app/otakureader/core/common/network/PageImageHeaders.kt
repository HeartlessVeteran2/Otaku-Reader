package app.otakureader.core.common.network

import app.otakureader.core.common.collection.BoundedCache
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Headers to attach when fetching a page image, looked up by image URL.
 *
 * ### Why a registry rather than parameters
 *
 * Page images are requested from **six** places — the zoomable page, the full-page gallery, the
 * thumbnail strip, two prefetchers and the reader's own warm-up. Threading headers through all
 * of them means every future call site has to remember, and the failure when one forgets is
 * near-undiagnosable: the host returns 403, the page renders blank, and nothing distinguishes it
 * from a dead link. Registering once where the page list is produced, and reading here inside
 * the image pipeline's interceptor, makes omission structurally impossible and gets the
 * prefetchers right for free.
 *
 * ### Why headers are needed at all
 *
 * Many hosts refuse an image request that arrives without a `Referer` naming the site it was
 * linked from — hotlink protection. A reader fetching the image directly looks exactly like a
 * hotlinker unless it says where it came from. Some sources also supply their own per-page
 * headers (a signed token, a session header), which must be preserved rather than replaced.
 *
 * ### Lookup order
 *
 * Exact URL first, then the URL's host. The host fallback matters because an image URL is not
 * always byte-identical to the one the source handed us — CDNs append cache-busting query
 * parameters, and redirects land on a sibling host. Falling back to the host keeps the `Referer`
 * attached through those, where an exact-match-only registry would silently stop working.
 *
 * ### Known limitation
 *
 * The host fallback is last-writer-wins, so if two sources serve images from the *same* CDN host
 * the second to fetch a page list overwrites the first's `Referer`. Entries are refreshed on
 * every page-list fetch, so this self-corrects as soon as the affected source is read again, and
 * per-page headers — which sources with real hotlink requirements supply — are unaffected. Stated
 * rather than papered over: there is no clear() here, because clearing on a source-list refresh
 * would strip the headers from the chapter the user is reading at that moment, which is a worse
 * failure than the one it would be trying to avoid.
 *
 * Thread-safe: registration happens on IO while lookups happen on OkHttp's dispatcher threads.
 */
@Singleton
class PageImageHeaders @Inject constructor() {

    private companion object {
        /**
         * Bounds on retained entries.
         *
         * A chapter is tens to a couple of hundred pages and a long session spans many chapters,
         * so an unbounded map would grow for as long as the reader is open. Eviction is
         * least-recently-used, which matches how pages are read: the chapter in front of the
         * user stays warm and old ones fall out.
         */
        const val MAX_PAGE_ENTRIES = 2_000
        const val MAX_HOST_ENTRIES = 64

        const val REFERER = "Referer"
    }

    private val perPage = BoundedCache<String, Map<String, String>>(MAX_PAGE_ENTRIES)
    private val perHost = BoundedCache<String, Map<String, String>>(MAX_HOST_ENTRIES)

    /**
     * Register the fallback `Referer` for every host in [pageUrls], derived from [sourceBaseUrl].
     *
     * Applies to every backend — an APK-backed source needs this exactly as much as a JavaScript
     * one, so it is registered from the shared repository path rather than inside either backend.
     */
    fun registerReferer(sourceBaseUrl: String, pageUrls: List<String>) {
        val referer = sourceBaseUrl.takeIf { it.isNotBlank() } ?: return
        val headers = mapOf(REFERER to referer)
        pageUrls.mapNotNull { it.hostOrNull() }.distinct().forEach { perHost[it] = headers }
    }

    /**
     * Register headers a source supplied for specific pages.
     *
     * Merged over the host fallback at lookup time rather than replacing it, so a source that
     * supplies only an auth header still gets a `Referer`, and one that supplies its own
     * `Referer` overrides ours.
     */
    fun registerPageHeaders(headersByUrl: Map<String, Map<String, String>>) {
        if (headersByUrl.isEmpty()) return
        perPage.putAll(headersByUrl)
    }

    /** Headers for [url], or empty when nothing was registered for it. */
    fun headersFor(url: String): Map<String, String> {
        val host = url.hostOrNull()
        val hostHeaders = host?.let { perHost[it] }.orEmpty()
        val pageHeaders = perPage[url].orEmpty()
        return hostHeaders + pageHeaders
    }

    private fun String.hostOrNull(): String? =
        runCatching { URI(this).host }.getOrNull()?.takeIf { it.isNotBlank() }
}
