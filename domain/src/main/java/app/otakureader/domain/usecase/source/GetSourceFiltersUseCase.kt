package app.otakureader.domain.usecase.source

import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.FilterList

/**
 * Use case for getting the available filters for a source.
 */
class GetSourceFiltersUseCase(
    private val sourceRepository: SourceRepository
) {
    suspend operator fun invoke(sourceId: String): FilterList {
        return sourceRepository.getSourceFilters(sourceId)
    }
}
