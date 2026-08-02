package app.otakureader.core.js.client

import app.otakureader.core.js.protocol.JsCallArgs
import app.otakureader.core.js.protocol.JsCallResult
import app.otakureader.core.js.protocol.JsMangaDetailDto
import app.otakureader.core.js.protocol.JsMangaListDto
import app.otakureader.core.js.protocol.JsPageDto
import app.otakureader.core.js.protocol.JsProtocol
import app.otakureader.core.js.protocol.JsSourceConfig
import app.otakureader.core.common.network.PageImageHeaders
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.Page
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga

/**
 * A JavaScript-backed source, presented to the app as an ordinary [MangaSource].
 *
 * This is the seam that makes the whole rebuild cheap: the reader, library, downloads,
 * migration and every domain use-case already speak [MangaSource], so none of them can tell
 * whether a source is an APK, a JavaScript file, or the local folder. Adding a second backend
 * is a new implementation of this interface rather than a change to any of them.
 *
 * A failing call throws, matching what [MangaSource] implementations are expected to do —
 * `SourceRepositoryImpl` already wraps every source call and routes failures through
 * `SourceHealthMonitor`, so a hung or broken script joins the same path as a dead website.
 */
class JsSource(
    private val config: JsSourceConfig,
    private val connection: JsEngineConnection,
    private val pageImageHeaders: PageImageHeaders,
) : MangaSource {

    override val id: String = config.id
    override val name: String = config.name
    override val lang: String = config.lang
    override val baseUrl: String = config.baseUrl
    override val isNsfw: Boolean = config.isNsfw

    override suspend fun fetchPopularManga(page: Int): MangaPage =
        mangaList(JsProtocol.Method.POPULAR, JsCallArgs(page = page))

    override suspend fun fetchLatestUpdates(page: Int): MangaPage =
        mangaList(JsProtocol.Method.LATEST, JsCallArgs(page = page))

    override suspend fun fetchSearchManga(page: Int, query: String, filters: FilterList): MangaPage =
        mangaList(
            JsProtocol.Method.SEARCH,
            JsCallArgs(page = page, query = query, filters = filters.toJsFilters()),
        )

    override suspend fun fetchMangaDetails(manga: SourceManga): SourceManga {
        val detail = decode<JsMangaDetailDto>(
            JsProtocol.Method.DETAIL,
            JsCallArgs(url = manga.url),
        )
        return manga.copy(
            title = detail.name.ifBlank { manga.title },
            thumbnailUrl = detail.imageUrl ?: manga.thumbnailUrl,
            description = detail.description ?: manga.description,
            author = detail.author ?: manga.author,
            artist = detail.artist ?: manga.artist,
            genre = detail.genre.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: manga.genre,
            // 0 is the DTO default and also means "unknown", so an omitted status is
            // indistinguishable from an explicit one. Treat it as absent and keep what we
            // already knew, rather than downgrading a known status to unknown on every refresh.
            status = detail.status.takeIf { it != 0 } ?: manga.status,
            initialized = true,
        )
    }

    override suspend fun fetchChapterList(manga: SourceManga): List<SourceChapter> {
        val detail = decode<JsMangaDetailDto>(
            JsProtocol.Method.DETAIL,
            JsCallArgs(url = manga.url),
        )
        return detail.chapters.map { chapter ->
            SourceChapter(
                url = chapter.url,
                name = chapter.name,
                // Sources report upload dates in wildly inconsistent formats. An unparseable
                // value becomes 0 ("unknown") rather than failing the whole chapter list —
                // losing one date is recoverable, losing the chapter list is not.
                dateUpload = chapter.dateUpload?.toLongOrNull() ?: 0L,
                scanlator = chapter.scanlator.orEmpty(),
            )
        }
    }

    override suspend fun fetchPageList(chapter: SourceChapter): List<Page> {
        val pages = decode<List<JsPageDto>>(
            JsProtocol.Method.PAGE_LIST,
            JsCallArgs(url = chapter.url),
        )

        // Per-page headers cannot travel on Page — it carries only an index and two URLs — so
        // they are handed to the image pipeline here instead. Registering during the same call
        // that produced the pages is deliberate: the earlier design exposed a separate
        // pageHeaders() accessor, which would have re-run getPageList in the engine a second
        // time for data this call already had, doubling the work and the network traffic behind
        // it for every chapter opened.
        pages.filter { it.headers.isNotEmpty() }
            .associate { it.url to it.headers }
            .let(pageImageHeaders::registerPageHeaders)

        return pages.mapIndexed { index, page ->
            // Both fields carry the image URL. Page.url means "this page's remote URL", not
            // the chapter's; downstream code falls back to it when imageUrl is blank, so
            // putting the chapter URL there would make that fallback fetch the wrong thing.
            Page(index = index, url = page.url, imageUrl = page.url)
        }
    }

    private suspend fun mangaList(method: String, args: JsCallArgs): MangaPage {
        val dto = decode<JsMangaListDto>(method, args)
        return MangaPage(
            mangas = dto.list.map { it.toSourceManga() },
            hasNextPage = dto.hasNextPage,
        )
    }

    private fun app.otakureader.core.js.protocol.JsMangaDto.toSourceManga() = SourceManga(
        url = link,
        title = name,
        thumbnailUrl = imageUrl,
        description = description,
        author = author,
        artist = artist,
        genre = genre.takeIf { it.isNotEmpty() }?.joinToString(", "),
        status = status,
    )

    /**
     * Run a call and decode its payload, converting any failure into an exception.
     *
     * The engine reports failures in-band ([JsCallResult.ok]) so that a script error is never a
     * binder exception; this is where that becomes a normal Kotlin failure for the app's
     * existing error handling to pick up.
     */
    private suspend inline fun <reified T> decode(method: String, args: JsCallArgs): T {
        val payload = payloadOf(connection.call(config.id, method, args), method)

        return try {
            JsProtocol.json.decodeFromString<T>(payload)
        } catch (e: Exception) {
            // A source whose shape does not match the contract is a source bug, and saying so
            // beats a bare SerializationException surfacing in the UI.
            throw JsSourceException(
                PROTOCOL_ERROR,
                "Source returned an unexpected shape for $method: ${e.message}",
            )
        }
    }

    /** Unwraps a call result, turning the engine's in-band failure into an exception. */
    @PublishedApi
    internal fun payloadOf(result: JsCallResult, method: String): String {
        if (!result.ok) {
            throw JsSourceException(result.errorKind.name, result.error ?: "JavaScript source failed")
        }
        return result.data
            ?: throw JsSourceException(PROTOCOL_ERROR, "Source returned no data for $method")
    }

    @PublishedApi
    internal companion object {
        const val PROTOCOL_ERROR = "PROTOCOL_ERROR"
    }
}

/** Failure originating in a JavaScript source, carrying the engine's classification. */
class JsSourceException(
    val kind: String,
    message: String,
) : Exception("[$kind] $message")

/**
 * Serialize active filters into the array shape a Mangayomi-format `search` expects.
 *
 * Only active filters are emitted. Sending the full list including untouched entries makes
 * sources treat "not selected" as a real constraint, which silently narrows results — worse
 * than sending nothing, because the search still looks like it worked.
 */
internal fun FilterList.toJsFilters(): String {
    val active = filters.mapNotNull { filter ->
        when (filter) {
            is app.otakureader.sourceapi.Filter.Select<*> ->
                filter.takeIf { it.state != 0 }?.let {
                    buildJsFilter("select", it.name, it.state.toString())
                }
            is app.otakureader.sourceapi.Filter.Text ->
                filter.takeIf { it.state.isNotBlank() }?.let {
                    buildJsFilter("text", it.name, JsProtocol.json.encodeToString(it.state))
                }
            is app.otakureader.sourceapi.Filter.CheckBox ->
                filter.takeIf { it.state }?.let { buildJsFilter("checkbox", it.name, "true") }
            is app.otakureader.sourceapi.Filter.TriState ->
                filter.takeIf { it.state != 0 }?.let {
                    buildJsFilter("tristate", it.name, it.state.toString())
                }
            is app.otakureader.sourceapi.Filter.Sort ->
                filter.state?.let {
                    buildJsFilter("sort", filter.name, """{"index":${it.index},"ascending":${it.ascending}}""")
                }
            else -> null
        }
    }
    return active.joinToString(prefix = "[", postfix = "]")
}

private fun buildJsFilter(type: String, name: String, state: String): String =
    """{"type":${JsProtocol.json.encodeToString(type)},""" +
        """"name":${JsProtocol.json.encodeToString(name)},"state":$state}"""
