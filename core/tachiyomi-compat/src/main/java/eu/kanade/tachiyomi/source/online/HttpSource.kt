package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSingle
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/**
 * A simple implementation for sources from a website.
 *
 * This is a SIMPLIFIED port of Tachiyomi/Komikku's HttpSource: it keeps the full public/protected
 * member surface extensions compile against, but drops the SY/EXH/KMK [DelegatedHttpSource]
 * machinery and per-source EH-logger injection.
 */
@Suppress("unused")
abstract class HttpSource : CatalogueSource {

    /**
     * Network service. Resolved from Injekt — the host registers a single [NetworkHelper].
     */
    protected val network: NetworkHelper by lazy { Injekt.get() }

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    abstract val baseUrl: String

    /**
     * Version id used to generate the source id.
     */
    open val versionId = 1

    /**
     * ID of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string `"${name.lowercase()}/$lang/$versionId"`.
     */
    override val id by lazy { generateId(name, lang, versionId) }

    /**
     * Headers used for requests.
     */
    open val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient
        get() = network.client

    /**
     * Generates a unique ID for the source based on the provided [name], [lang] and [versionId].
     *
     * @since extensions-lib 1.5
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    protected open fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgentProvider())
    }

    /**
     * Visible name of the source.
     */
    override fun toString() = "$name (${lang.uppercase()})"

    // ---- Popular ----

    override suspend fun getPopularManga(page: Int): MangasPage {
        @Suppress("DEPRECATION")
        return fetchPopularManga(page).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularManga(page)"))
    open fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

    protected abstract fun popularMangaRequest(page: Int): Request

    protected abstract fun popularMangaParse(response: Response): MangasPage

    // ---- Search ----

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        @Suppress("DEPRECATION")
        return fetchSearchManga(page, query, filters).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchManga(page, query, filters)"))
    open fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return Observable.defer {
            try {
                client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess()
            } catch (e: NoClassDefFoundError) {
                // RxJava doesn't handle Errors, which tends to happen during global searches
                // if an old extension using non-existent classes is still around
                throw RuntimeException(e)
            }
        }
            .map { response ->
                searchMangaParse(response)
            }
    }

    protected abstract fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request

    protected abstract fun searchMangaParse(response: Response): MangasPage

    // ---- Latest ----

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        @Suppress("DEPRECATION")
        return fetchLatestUpdates(page).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates(page)"))
    open fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response)
            }
    }

    abstract fun latestUpdatesRequest(page: Int): Request

    abstract fun latestUpdatesParse(response: Response): MangasPage

    // ---- Details ----

    override suspend fun getMangaDetails(manga: SManga): SManga {
        @Suppress("DEPRECATION")
        return fetchMangaDetails(manga).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getMangaDetails(manga)"))
    open fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    open fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    protected abstract fun mangaDetailsParse(response: Response): SManga

    // KMK -->

    /**
     * Whether parsing related mangas in manga page or extension provide custom related mangas request.
     *
     * @since komikku/extensions-lib 1.6
     */
    override val supportsRelatedMangas: Boolean get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = coroutineScope {
        async {
            client.newCall(relatedMangaListRequest(manga))
                // Cancellable: this runs inside an `async` a caller can cancel, and a blocking
                // execute() would keep the request alive after that (#1231).
                .await()
                .let { response ->
                    relatedMangaListParse(response)
                }
        }.await()
    }

    protected open fun relatedMangaListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    protected open fun relatedMangaListParse(response: Response): List<SManga> = popularMangaParse(response).mangas
    // KMK <--

    // ---- Chapters ----

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        @Suppress("DEPRECATION")
        return fetchChapterList(manga).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getChapterList(manga)"))
    open fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response)
            }
    }

    protected open fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    protected abstract fun chapterListParse(response: Response): List<SChapter>

    protected open fun chapterPageParse(response: Response): SChapter = throw UnsupportedOperationException("Not used!")

    // ---- Pages ----

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        @Suppress("DEPRECATION")
        return fetchPageList(chapter).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPageList(chapter)"))
    open fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response)
            }
    }

    protected open fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    protected abstract fun pageListParse(response: Response): List<Page>

    // ---- Image URL ----

    @Suppress("DEPRECATION")
    open suspend fun getImageUrl(page: Page): String {
        return fetchImageUrl(page).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getImageUrl(page)"))
    open fun fetchImageUrl(page: Page): Observable<String> {
        return client.newCall(imageUrlRequest(page))
            .asObservableSuccess()
            .map { imageUrlParse(it) }
    }

    protected open fun imageUrlRequest(page: Page): Request {
        return GET(page.url, headers)
    }

    protected abstract fun imageUrlParse(response: Response): String

    // ---- Image ----

    open suspend fun getImage(page: Page): Response {
        return client.newCachelessCallWithProgress(imageRequest(page), page)
            .awaitSuccess()
    }

    protected open fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }

    // ---- URL helpers ----

    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig.replace(" ", "%20"))
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }

    open fun getMangaUrl(manga: SManga): String {
        return mangaDetailsRequest(manga).url.toString()
    }

    open fun getChapterUrl(chapter: SChapter): String {
        return pageListRequest(chapter).url.toString()
    }

    /**
     * Called before inserting a new chapter into database. Use it if you need to override chapter
     * fields, like the title or the chapter number. Do not change anything to [manga].
     */
    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {}

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = FilterList()
}
