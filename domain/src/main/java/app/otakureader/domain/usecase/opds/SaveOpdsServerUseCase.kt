package app.otakureader.domain.usecase.opds

import app.otakureader.domain.model.OpdsServer
import app.otakureader.domain.repository.OpdsRepository

/**
 * Use case for adding or updating an OPDS server.
 */
class SaveOpdsServerUseCase(
    private val opdsRepository: OpdsRepository
) {
    suspend operator fun invoke(server: OpdsServer): Long {
        require(server.name.isNotBlank()) { "Server name cannot be blank" }
        require(server.url.isNotBlank()) { "Server URL cannot be blank" }
        return opdsRepository.saveServer(server)
    }
}
