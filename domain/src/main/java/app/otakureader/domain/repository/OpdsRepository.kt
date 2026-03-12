package app.otakureader.domain.repository

import app.otakureader.domain.model.OpdsFeed
import app.otakureader.domain.model.OpdsServer
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing OPDS servers and browsing OPDS catalogs.
 */
interface OpdsRepository {

    /** Get all saved OPDS servers as a reactive stream. */
    fun getServers(): Flow<List<OpdsServer>>

    /** Get a specific server by ID. */
    suspend fun getServer(serverId: Long): OpdsServer?

    /** Add or update an OPDS server. Returns the server ID. */
    suspend fun saveServer(server: OpdsServer): Long

    /** Delete an OPDS server by ID. */
    suspend fun deleteServer(serverId: Long)

    /** Browse an OPDS catalog feed at the given URL. */
    suspend fun browseCatalog(server: OpdsServer, feedUrl: String): Result<OpdsFeed>

    /** Search an OPDS catalog using the OpenSearch URL. */
    suspend fun searchCatalog(server: OpdsServer, searchUrl: String, query: String): Result<OpdsFeed>
}
