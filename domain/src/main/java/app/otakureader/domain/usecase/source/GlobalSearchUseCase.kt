package app.otakureader.domain.usecase.source

import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.MangaPage

/**
 * Use case for searching manga in a single source by query string (page 1).
 * The fan-out across all sources is orchestrated by the caller (e.g., GlobalSearchViewModel).
 */
class GlobalSearchUseCase(
    private val sourceRepository: SourceRepository
) {
    suspend operator fun invoke(sourceId: String, query: String): Result<MangaPage> {
        return sourceRepository.searchManga(sourceId, query, 1)
    }
}
