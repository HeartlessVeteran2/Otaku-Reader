package app.otakureader.domain.usecase.opds

import app.otakureader.domain.model.OpdsFeed
import app.otakureader.domain.model.OpdsServer
import app.otakureader.domain.repository.OpdsRepository

/**
 * Use case for browsing an OPDS catalog at a given feed URL.
 */
class BrowseOpdsCatalogUseCase(
    private val opdsRepository: OpdsRepository
) {
    suspend operator fun invoke(server: OpdsServer, feedUrl: String): Result<OpdsFeed> {
        return opdsRepository.browseCatalog(server, feedUrl)
    }
}
