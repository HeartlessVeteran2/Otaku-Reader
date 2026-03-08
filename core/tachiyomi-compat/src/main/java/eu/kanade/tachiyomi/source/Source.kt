// Stub matching tachiyomiorg/extensions-lib — used at compile time only.
// At runtime the real implementations come from the extension APKs.
package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

@Suppress("unused")
interface Source {
    val id: Long
    val name: String

    suspend fun getMangaDetails(manga: SManga): SManga =
        fetchMangaDetails(manga).toBlocking().single()

    suspend fun getChapterList(manga: SManga): List<SChapter> =
        fetchChapterList(manga).toBlocking().single()

    fun fetchPageList(chapter: SChapter): Observable<List<Page>>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getMangaDetails"))
    fun fetchMangaDetails(manga: SManga): Observable<SManga>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getChapterList"))
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>>
}
