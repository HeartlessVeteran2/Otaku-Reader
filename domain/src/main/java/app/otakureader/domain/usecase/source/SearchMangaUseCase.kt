package app.otakureader.domain.usecase.source

import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaPage

/**
 * Use case for searching manga in a source.
 */
class SearchMangaUseCase(
    private val sourceRepository: SourceRepository
) {
    suspend operator fun invoke(
        sourceId: String,
        query: String,
        page: Int = 1,
        filters: FilterList = FilterList()
    ): Result<MangaPage> {
        return sourceRepository.searchManga(sourceId, query, page, filters)
    }
}
