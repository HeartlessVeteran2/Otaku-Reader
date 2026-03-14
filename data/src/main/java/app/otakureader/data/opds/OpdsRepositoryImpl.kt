package app.otakureader.data.opds

import app.otakureader.core.database.dao.OpdsServerDao
import app.otakureader.core.database.entity.OpdsServerEntity
import app.otakureader.core.preferences.EncryptedOpdsCredentialStore
import app.otakureader.domain.model.OpdsFeed
import app.otakureader.domain.model.OpdsServer
import app.otakureader.domain.repository.OpdsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpdsRepositoryImpl @Inject constructor(
    private val opdsServerDao: OpdsServerDao,
    private val opdsClient: OpdsClient,
    private val credentialStore: EncryptedOpdsCredentialStore
) : OpdsRepository {

    override fun getServers(): Flow<List<OpdsServer>> {
        return opdsServerDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getServer(serverId: Long): OpdsServer? {
        return opdsServerDao.getById(serverId)?.toDomainWithCredentials()
    }

    override suspend fun saveServer(server: OpdsServer): Long {
        val id = if (server.id == 0L) {
            opdsServerDao.insert(server.toEntity())
        } else {
            opdsServerDao.update(server.toEntity())
            server.id
        }
        credentialStore.saveCredentials(id, server.username, server.password)
        return id
    }

    override suspend fun deleteServer(serverId: Long) {
        opdsServerDao.deleteById(serverId)
        credentialStore.deleteCredentials(serverId)
    }

    override suspend fun browseCatalog(server: OpdsServer, feedUrl: String): Result<OpdsFeed> {
        return try {
            val serverWithCreds = ensureCredentials(server)
            val feed = opdsClient.fetchFeed(serverWithCreds, feedUrl)
            Result.success(feed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchCatalog(
        server: OpdsServer,
        searchUrl: String,
        query: String
    ): Result<OpdsFeed> {
        return try {
            val serverWithCreds = ensureCredentials(server)
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
            // Try to resolve a search template from the OpenSearch description URL first,
            // then fall back to treating searchUrl itself as a template.
            val template = opdsClient.fetchSearchTemplate(serverWithCreds, searchUrl)
            val actualSearchUrl = (template ?: searchUrl).replace("{searchTerms}", encodedQuery)
            val feed = opdsClient.fetchFeed(serverWithCreds, actualSearchUrl)
            Result.success(feed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ensures the server object has credentials loaded from encrypted storage.
     * If credentials are already populated (non-blank), they are used as-is.
     */
    private suspend fun ensureCredentials(server: OpdsServer): OpdsServer {
        if (server.username.isNotBlank() || server.password.isNotBlank()) return server
        val username = credentialStore.getUsername(server.id)
        val password = credentialStore.getPassword(server.id)
        return server.copy(username = username, password = password)
    }

    /** Converts entity to domain model (without credentials — use for list display). */
    private fun OpdsServerEntity.toDomain(): OpdsServer = OpdsServer(
        id = id,
        name = name,
        url = url
    )

    /** Converts entity to domain model with credentials loaded from encrypted store. */
    private suspend fun OpdsServerEntity.toDomainWithCredentials(): OpdsServer {
        val username = credentialStore.getUsername(id)
        val password = credentialStore.getPassword(id)
        return OpdsServer(
            id = id,
            name = name,
            url = url,
            username = username,
            password = password
        )
    }

    private fun OpdsServer.toEntity(): OpdsServerEntity = OpdsServerEntity(
        id = id,
        name = name,
        url = url
    )
}
