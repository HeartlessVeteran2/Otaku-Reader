package app.otakureader.domain.usecase.opds

import app.otakureader.domain.repository.OpdsRepository

/**
 * Use case for deleting an OPDS server.
 */
class DeleteOpdsServerUseCase(
    private val opdsRepository: OpdsRepository
) {
    suspend operator fun invoke(serverId: Long) {
        opdsRepository.deleteServer(serverId)
    }
}
