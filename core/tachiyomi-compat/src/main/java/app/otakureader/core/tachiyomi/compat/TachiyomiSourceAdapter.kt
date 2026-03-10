package app.otakureader.core.tachiyomi.compat

import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.Page
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rx.Observable

/**
 * Adapter that wraps a Tachiyomi CatalogueSource and exposes it as an Otaku Reader MangaSource.
 *
 * This class handles the delegation of all calls from the Otaku Reader interface to the
 * underlying Tachiyomi source, with proper model conversion.
 */
class TachiyomiSourceAdapter(
    private val tachiyomiSource: CatalogueSource,
    override val isNsfw: Boolean = false
) : MangaSource {

    override val id: String
        get() = tachiyomiSource.id.toString()

    override val name: String
        get() = tachiyomiSource.name

    override val lang: String
        get() = tachiyomiSource.lang

    override val baseUrl: String
        get() = tachiyomiSource.baseUrl

    override val supportsLatest: Boolean
        get() = tachiyomiSource.supportsLatest

    val supportsSearch: Boolean = true

    val headers: Map<String, String>
        get() = tachiyomiSource.headers.toMap()

    /**
     * Get popular manga from the source
     */
    override suspend fun fetchPopularManga(page: Int): MangaPage {
        return withContext(Dispatchers.IO) {
            val mangasPage = tachiyomiSource.fetchPopularManga(page).toBlocking().first()
            TachiyomiModelsAdapter.toMangaPage(mangasPage)
        }
    }

    /**
     * Get latest updates from the source
     */
    override suspend fun fetchLatestUpdates(page: Int): MangaPage {
        return withContext(Dispatchers.IO) {
            val mangasPage = tachiyomiSource.fetchLatestUpdates(page).toBlocking().first()
            TachiyomiModelsAdapter.toMangaPage(mangasPage)
        }
    }

    /**
     * Search manga in the source
     */
    override suspend fun fetchSearchManga(page: Int, query: String, filters: FilterList): MangaPage {
        return withContext(Dispatchers.IO) {
            val tachiyomiFilters = convertFilters(filters)
            val mangasPage = tachiyomiSource.fetchSearchManga(page, query, tachiyomiFilters).toBlocking().first()
            TachiyomiModelsAdapter.toMangaPage(mangasPage)
        }
    }

    /**
     * Search manga with query only (convenience method)
     */
    suspend fun searchManga(query: String, page: Int): MangaPage {
        return withContext(Dispatchers.IO) {
            val filterList = eu.kanade.tachiyomi.source.model.FilterList()
            val mangasPage = tachiyomiSource.fetchSearchManga(page, query, filterList).toBlocking().first()
            TachiyomiModelsAdapter.toMangaPage(mangasPage)
        }
    }

    /**
     * Get chapter list for a manga
     */
    override suspend fun fetchChapterList(manga: SourceManga): List<SourceChapter> {
        return withContext(Dispatchers.IO) {
            val sManga = TachiyomiModelsAdapter.toTachiyomiSManga(manga)
            val sChapters = tachiyomiSource.fetchChapterList(sManga).toBlocking().first()
            TachiyomiModelsAdapter.toSourceChapterList(sChapters)
        }
    }

    /**
     * Get page list for a chapter
     */
    override suspend fun fetchPageList(chapter: SourceChapter): List<Page> {
        return withContext(Dispatchers.IO) {
            val sChapter = TachiyomiModelsAdapter.toTachiyomiSChapter(chapter)
            val sPages = tachiyomiSource.fetchPageList(sChapter).toBlocking().first()
            TachiyomiModelsAdapter.toPageList(sPages, chapter.hashCode().toLong())
        }
    }

    /**
     * Get manga details (full information)
     */
    override suspend fun fetchMangaDetails(manga: SourceManga): SourceManga {
        return withContext(Dispatchers.IO) {
            val sManga = TachiyomiModelsAdapter.toTachiyomiSManga(manga)
            val detailedManga = tachiyomiSource.fetchMangaDetails(sManga).toBlocking().first()
            TachiyomiModelsAdapter.toSourceManga(
                TachiyomiModelsAdapter.fromTachiyomiSManga(detailedManga),
            ).copy(url = manga.url)
        }
    }

    /**
     * Get the source's filter list
     */
    fun getFilterList(): eu.kanade.tachiyomi.source.model.FilterList {
        return tachiyomiSource.getFilterList()
    }

    /**
     * Get available sort options for the source
     */
    fun getSortOptions(): List<Pair<String?, String>> {
        return tachiyomiSource.getFilterList()
            .filterIsInstance<eu.kanade.tachiyomi.source.model.Filter.Sort>()
            .flatMap { sortFilter ->
                sortFilter.values.mapIndexed { index, name ->
                    index.toString() to name
                }
            }
    }

    /**
     * Convert Otaku Reader FilterList to Tachiyomi FilterList
     */
    private fun convertFilters(filters: FilterList): eu.kanade.tachiyomi.source.model.FilterList {
        // For now, return empty Tachiyomi FilterList
        // In a full implementation, we would convert each filter type
        return eu.kanade.tachiyomi.source.model.FilterList()
    }

    /**
     * Extension function to convert OkHttp Headers to Map
     */
    private fun okhttp3.Headers.toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until size) {
            map[name(i)] = value(i)
        }
        return map
    }

    /**
     * Generate a unique chapter ID based on URL and manga URL
     */
    private fun generateChapterId(chapterUrl: String, mangaUrl: String): Long {
        return (mangaUrl + chapterUrl).hashCode().toLong()
    }
}

/**
 * Extension function to convert RxJava Observable to blocking first
 */
private fun <T> Observable<T>.toBlocking(): Observable<T> = this
