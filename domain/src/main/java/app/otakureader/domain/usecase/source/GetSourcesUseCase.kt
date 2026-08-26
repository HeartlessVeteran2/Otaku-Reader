package app.otakureader.domain.usecase.source

import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.MangaSource
import kotlinx.coroutines.flow.Flow

/**
 * Use case for getting all available sources.
 */
class GetSourcesUseCase(
    private val sourceRepository: SourceRepository
) {
    operator fun invoke(): Flow<List<MangaSource>> {
        return sourceRepository.getSources()
    }

    /**
     * Whether the initial source load is still running (#1258).
     *
     * Paired with [invoke] rather than split into its own use case because the two are only
     * meaningful together: an empty list from [invoke] means "no sources installed" when this is
     * false and "not loaded yet" when it is true, and a caller that reads one without the other
     * cannot tell those apart.
     */
    fun isLoading(): Flow<Boolean> = sourceRepository.isLoadingSources()
}
