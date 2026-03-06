package app.komikku.sourceapi

import kotlinx.serialization.Serializable

/**
 * Public API contract for manga source extensions.
 * Extensions must implement [MangaSource] to integrate with Komikku.
 *
 * Related types:
 * - [MangaPage] / [SourceManga] / [SourceChapter] — defined in this file
 * - [Page] — see Page.kt
 * - [Filter] / [FilterList] — see Filter.kt (richer hierarchy with Select, TriState, Sort, etc.)
 */
interface MangaSource {
    /** Unique identifier for this source (e.g., "en.mangadex"). */
    val id: String

    /** Human-readable name shown in the UI. */
    val name: String

    /** Language code (ISO 639-1), e.g., "en", "ja". */
    val lang: String

    /** Base URL of the source website. */
    val baseUrl: String

    /** Whether this source supports fetching the latest updates feed. */
    val supportsLatest: Boolean get() = true

    /** Fetch a paginated list of popular manga. */
    suspend fun fetchPopularManga(page: Int): MangaPage

    /** Fetch a paginated list of latest manga updates. */
    suspend fun fetchLatestUpdates(page: Int): MangaPage

    /** Fetch manga details for a given source manga. */
    suspend fun fetchMangaDetails(manga: SourceManga): SourceManga

    /** Fetch the chapter list for a given manga. */
    suspend fun fetchChapterList(manga: SourceManga): List<SourceChapter>

    /** Fetch the page list for a given chapter. */
    suspend fun fetchPageList(chapter: SourceChapter): List<Page>

    /** Search manga by query string and optional filters. */
    suspend fun fetchSearchManga(page: Int, query: String, filters: FilterList): MangaPage
}

/** Paginated result from a source query. */
data class MangaPage(
    val mangas: List<SourceManga>,
    val hasNextPage: Boolean
)

/** Manga data as returned by a remote source. */
@Serializable
data class SourceManga(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genre: String? = null,
    val status: Int = 0,
    val initialized: Boolean = false
)

/** Chapter data as returned by a remote source. */
@Serializable
data class SourceChapter(
    val url: String,
    val name: String,
    val dateUpload: Long = 0L,
    val chapterNumber: Float = -1f,
    val scanlator: String? = null
)
