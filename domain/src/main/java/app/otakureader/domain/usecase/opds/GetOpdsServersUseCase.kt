package app.otakureader.domain.usecase.opds

import app.otakureader.domain.model.OpdsServer
import app.otakureader.domain.repository.OpdsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for getting all saved OPDS servers.
 */
class GetOpdsServersUseCase(
    private val opdsRepository: OpdsRepository
) {
    operator fun invoke(): Flow<List<OpdsServer>> {
        return opdsRepository.getServers()
    }
}
