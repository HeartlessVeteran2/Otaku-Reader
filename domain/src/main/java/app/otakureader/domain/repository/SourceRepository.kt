package app.otakureader.domain.repository

import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceManga
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing manga sources and fetching manga from them.
 */
interface SourceRepository {

    /**
     * Get all available sources
     */
    fun getSources(): Flow<List<MangaSource>>

    /**
     * Get a source by its ID
     */
    suspend fun getSource(sourceId: String): MangaSource?

    /**
     * Get popular manga from a source
     */
    suspend fun getPopularManga(sourceId: String, page: Int): Result<MangaPage>

    /**
     * Get latest updates from a source
     */
    suspend fun getLatestUpdates(sourceId: String, page: Int): Result<MangaPage>

    /**
     * Search manga in a source
     */
    suspend fun searchManga(sourceId: String, query: String, page: Int): Result<MangaPage>

    /**
     * Get manga details from a source
     */
    suspend fun getMangaDetails(sourceId: String, manga: SourceManga): Result<SourceManga>

    /**
     * Load Tachiyomi extension from APK
     */
    suspend fun loadExtension(apkPath: String): Result<Unit>

    /**
     * Load Tachiyomi extension from URL
     */
    suspend fun loadExtensionFromUrl(url: String): Result<Unit>

    /**
     * Refresh the list of available sources
     */
    suspend fun refreshSources()
}
