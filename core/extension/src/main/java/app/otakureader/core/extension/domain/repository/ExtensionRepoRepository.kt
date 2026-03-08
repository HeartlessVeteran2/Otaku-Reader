package app.otakureader.core.extension.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing extension repository URLs.
 * Supports adding/removing third-party extension repositories (e.g., Keiyoushi, Komikku).
 */
interface ExtensionRepoRepository {

    /**
     * Get all configured repository URLs.
     */
    fun getRepositories(): Flow<List<String>>

    /**
     * Add a new repository URL.
     * @param url The repository URL (should point to an index.json endpoint)
     */
    suspend fun addRepository(url: String)

    /**
     * Remove a repository URL.
     * @param url The repository URL to remove
     */
    suspend fun removeRepository(url: String)

    /**
     * Get the default/active repository URL.
     */
    suspend fun getActiveRepository(): String

    /**
     * Set the active repository URL.
     */
    suspend fun setActiveRepository(url: String)

    /**
     * Clear all repositories.
     */
    suspend fun clearRepositories()
}
