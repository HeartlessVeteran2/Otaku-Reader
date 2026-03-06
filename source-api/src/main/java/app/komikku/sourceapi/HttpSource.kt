package app.komikku.sourceapi

interface HttpSource : Source {
    val baseUrl: String
    suspend fun getPopularManga(page: Int): MangasPage
    suspend fun getLatestUpdates(page: Int): MangasPage
    suspend fun searchManga(page: Int, query: String, filters: FilterList = FilterList()): MangasPage
    suspend fun getMangaDetails(manga: SManga): SManga
    suspend fun getChapterList(manga: SManga): List<SChapter>
    suspend fun getPageList(chapter: SChapter): List<Page>
}
