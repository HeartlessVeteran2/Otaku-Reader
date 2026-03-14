package app.otakureader.data.opds

import app.otakureader.domain.model.OpdsFeed
import app.otakureader.domain.model.OpdsServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP client for fetching and parsing OPDS catalog feeds.
 */
@Singleton
class OpdsClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    /**
     * Fetches and parses an OPDS feed from the given URL.
     * Resolves relative URLs against the server's base URL.
     */
    suspend fun fetchFeed(server: OpdsServer, feedUrl: String): OpdsFeed =
        withContext(Dispatchers.IO) {
            val resolvedUrl = resolveUrl(server.url, feedUrl)
            val requestBuilder = Request.Builder().url(resolvedUrl)

            if (server.username.isNotBlank()) {
                requestBuilder.header(
                    "Authorization",
                    Credentials.basic(server.username, server.password)
                )
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OpdsException("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw OpdsException("Empty response body")
                body.byteStream().use { stream ->
                    val feed = OpdsParser.parse(stream)
                    // Resolve relative URLs in the feed
                    feed.copy(
                        entries = feed.entries.map { entry ->
                            entry.copy(
                                thumbnailUrl = entry.thumbnailUrl?.let {
                                    resolveUrl(resolvedUrl, it)
                                },
                                links = entry.links.map { link ->
                                    link.copy(href = resolveUrl(resolvedUrl, link.href))
                                }
                            )
                        },
                        links = feed.links.map { link ->
                            link.copy(href = resolveUrl(resolvedUrl, link.href))
                        },
                        searchUrl = feed.searchUrl?.let { resolveUrl(resolvedUrl, it) }
                    )
                }
            }
        }

    /**
     * Fetches an OpenSearch description and extracts the search template URL.
     */
    suspend fun fetchSearchTemplate(server: OpdsServer, searchDescriptionUrl: String): String? =
        withContext(Dispatchers.IO) {
            val resolvedUrl = resolveUrl(server.url, searchDescriptionUrl)
            val requestBuilder = Request.Builder().url(resolvedUrl)

            if (server.username.isNotBlank()) {
                requestBuilder.header(
                    "Authorization",
                    Credentials.basic(server.username, server.password)
                )
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body ?: return@withContext null
                body.byteStream().use { stream ->
                    OpenSearchParser.parseTemplate(stream)
                }
            }
        }

    companion object {
        /**
         * Resolves a potentially relative URL against a base URL.
         */
        fun resolveUrl(baseUrl: String, relativeUrl: String): String {
            if (relativeUrl.isBlank()) return baseUrl
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                return relativeUrl
            }

            return try {
                val base = URI(baseUrl)
                base.resolve(relativeUrl).toString()
            } catch (_: Exception) {
                // Fallback: simple concatenation
                val cleanBase = baseUrl.trimEnd('/')
                val cleanRelative = relativeUrl.trimStart('/')
                "$cleanBase/$cleanRelative"
            }
        }
    }
}
