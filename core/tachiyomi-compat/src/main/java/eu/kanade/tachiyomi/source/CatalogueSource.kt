// Stub matching tachiyomiorg/extensions-lib — used at compile time only.
package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import rx.Observable

@Suppress("unused")
interface CatalogueSource : Source {
    val lang: String
    val supportsLatest: Boolean
    val baseUrl: String get() = ""
    val headers: okhttp3.Headers get() = okhttp3.Headers.headersOf()

    fun fetchPopularManga(page: Int): Observable<MangasPage>
    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage>
    fun fetchLatestUpdates(page: Int): Observable<MangasPage>
    fun getFilterList(): FilterList
}
