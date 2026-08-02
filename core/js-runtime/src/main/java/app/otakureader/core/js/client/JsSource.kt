package app.otakureader.core.js.client

import app.otakureader.core.js.protocol.JsCallArgs
import app.otakureader.core.js.protocol.JsCallResult
import app.otakureader.core.js.protocol.JsMangaDetailDto
import app.otakureader.core.js.protocol.JsMangaListDto
import app.otakureader.core.js.protocol.JsPageDto
import app.otakureader.core.js.protocol.JsProtocol
import app.otakureader.core.js.protocol.JsSourceConfig
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
        mangaList(JsProtocol.Method.SEARCH, JsCallArgs(page = page, query = query))

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
            status = detail.status,
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
        return pages.mapIndexed { index, page ->
            Page(index = index, url = chapter.url, imageUrl = page.url)
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
