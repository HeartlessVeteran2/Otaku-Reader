package app.otakureader.domain.usecase.opds

import app.otakureader.domain.model.OpdsFeed
import app.otakureader.domain.model.OpdsServer
import app.otakureader.domain.repository.OpdsRepository

/**
 * Use case for searching an OPDS catalog.
 */
class SearchOpdsCatalogUseCase(
    private val opdsRepository: OpdsRepository
) {
    suspend operator fun invoke(server: OpdsServer, searchUrl: String, query: String): Result<OpdsFeed> {
        require(query.isNotBlank()) { "Search query cannot be blank" }
        return opdsRepository.searchCatalog(server, searchUrl, query)
    }
}
