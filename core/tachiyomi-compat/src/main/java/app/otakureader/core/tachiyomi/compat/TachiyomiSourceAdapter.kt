package app.otakureader.core.tachiyomi.compat

import app.otakureader.sourceapi.Filter
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.Filters
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
     * Get the source's filter list in source-api format.
     */
    override fun getFilterList(): FilterList {
        val tachiyomiFilters = tachiyomiSource.getFilterList()
        return FilterList(tachiyomiFilters.map { convertTachiyomiFilter(it) })
    }

    /**
     * Get the source's raw Tachiyomi filter list.
     */
    fun getTachiyomiFilterList(): eu.kanade.tachiyomi.source.model.FilterList {
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
     * Convert an Otaku Reader FilterList to a Tachiyomi FilterList by getting a fresh
     * Tachiyomi filter list and applying the states from the source-api filters.
     */
    private fun convertFilters(filters: FilterList): eu.kanade.tachiyomi.source.model.FilterList {
        if (filters.filters.isEmpty()) {
            return eu.kanade.tachiyomi.source.model.FilterList()
        }
        // Get a fresh Tachiyomi filter list and apply the source-api filter states
        val tachiyomiFilters = tachiyomiSource.getFilterList()
        applyStates(filters.filters, tachiyomiFilters.toList())
        return tachiyomiFilters
    }

    /**
     * Walk two parallel filter lists and copy states from source-api filters to Tachiyomi filters.
     */
    private fun applyStates(
        sourceFilters: List<Filter<*>>,
        tachiyomiFilters: List<eu.kanade.tachiyomi.source.model.Filter<*>>
    ) {
        val count = minOf(sourceFilters.size, tachiyomiFilters.size)
        for (i in 0 until count) {
            val src = sourceFilters[i]
            val dst = tachiyomiFilters[i]
            @Suppress("UNCHECKED_CAST")
            when {
                src is Filter.Select<*> && dst is eu.kanade.tachiyomi.source.model.Filter.Select<*> -> {
                    (dst as eu.kanade.tachiyomi.source.model.Filter<Int>).state = src.state
                }
                src is Filter.Text && dst is eu.kanade.tachiyomi.source.model.Filter.Text -> {
                    (dst as eu.kanade.tachiyomi.source.model.Filter<String>).state = src.state
                }
                src is Filter.CheckBox && dst is eu.kanade.tachiyomi.source.model.Filter.CheckBox -> {
                    (dst as eu.kanade.tachiyomi.source.model.Filter<Boolean>).state = src.state
                }
                src is Filter.TriState && dst is eu.kanade.tachiyomi.source.model.Filter.TriState -> {
                    (dst as eu.kanade.tachiyomi.source.model.Filter<Int>).state = src.state
                }
                src is Filter.Sort && dst is eu.kanade.tachiyomi.source.model.Filter.Sort -> {
                    val sel = src.state
                    (dst as eu.kanade.tachiyomi.source.model.Filter<eu.kanade.tachiyomi.source.model.Filter.Sort.Selection?>).state =
                        sel?.let { eu.kanade.tachiyomi.source.model.Filter.Sort.Selection(it.index, it.ascending) }
                }
                src is Filter.Group<*> && dst is eu.kanade.tachiyomi.source.model.Filter.Group<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    applyStates(
                        src.state as List<Filter<*>>,
                        (dst as eu.kanade.tachiyomi.source.model.Filter.Group<eu.kanade.tachiyomi.source.model.Filter<*>>).state
                    )
                }
            }
        }
    }

    /**
     * Convert a single Tachiyomi filter to a source-api filter.
     */
    private fun convertTachiyomiFilter(
        filter: eu.kanade.tachiyomi.source.model.Filter<*>
    ): Filter<*> {
        return when (filter) {
            is eu.kanade.tachiyomi.source.model.Filter.Header ->
                Filter.Header(filter.name)
            is eu.kanade.tachiyomi.source.model.Filter.Separator ->
                Filter.Separator(filter.name)
            is eu.kanade.tachiyomi.source.model.Filter.Select<*> ->
                Filters.SelectFilter(
                    filter.name,
                    filter.values.map { it.toString() }.toTypedArray(),
                    filter.state
                )
            is eu.kanade.tachiyomi.source.model.Filter.Text ->
                Filters.TextFilter(filter.name, filter.state)
            is eu.kanade.tachiyomi.source.model.Filter.CheckBox ->
                Filters.CheckBoxFilter(filter.name, filter.state)
            is eu.kanade.tachiyomi.source.model.Filter.TriState ->
                Filters.TriStateFilter(filter.name, filter.state)
            is eu.kanade.tachiyomi.source.model.Filter.Sort -> {
                val sel = filter.state
                val sourceSelection = sel?.let { Filter.Sort.Selection(it.index, it.ascending) }
                Filters.SortFilter(filter.name, filter.values, sourceSelection)
            }
            is eu.kanade.tachiyomi.source.model.Filter.Group<*> -> {
                @Suppress("UNCHECKED_CAST")
                val childFilters = (filter.state as List<eu.kanade.tachiyomi.source.model.Filter<*>>)
                    .map { convertTachiyomiFilter(it) }
                Filters.GroupFilter(filter.name, childFilters)
            }
        }
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
