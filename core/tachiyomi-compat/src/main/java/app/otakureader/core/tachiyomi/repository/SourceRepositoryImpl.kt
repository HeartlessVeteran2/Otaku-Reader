package app.otakureader.core.tachiyomi.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.tachiyomi.compat.TachiyomiExtensionLoader
import app.otakureader.core.tachiyomi.health.SourceHealthMonitor
import app.otakureader.core.tachiyomi.local.LocalSource
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of SourceRepository using Tachiyomi extension adapters.
 * Also includes the built-in [LocalSource] for on-device manga.
 *
 * Integrates [SourceHealthMonitor] to track source failures and prevent
 * repeated requests to dead/failing sources (inspired by Komikku's health monitoring).
 */
class SourceRepositoryImpl(
    private val context: Context,
    private val localSourcePreferences: LocalSourcePreferences,
    private val healthMonitor: SourceHealthMonitor
) : SourceRepository {

    /**
     * Secondary constructor for tests or other call-sites that already know the directory path
     * and do not have a [LocalSourcePreferences] instance available.
     *
     * Note: [healthMonitor] must be provided explicitly to avoid bypassing DI and accidentally
     * creating a separate monitor instance in production code.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    constructor(
        context: Context,
        localDirectory: String,
        healthMonitor: SourceHealthMonitor
    ) : this(
        context,
        LocalSourcePreferences.ofDirectory(localDirectory),
        healthMonitor
    )
        context.packageManager,
        context.cacheDir
    )

    /**
     * Returns a fresh [LocalSource] using the current scan directory from preferences.
     * Reading from the Flow is deferred to suspend call-sites so no blocking occurs at init.
     */
    private suspend fun currentLocalSource(): LocalSource {
        val dir = localSourcePreferences.localSourceDirectory.first()
        return LocalSource(context, dir)
    }

    private val _sources = MutableStateFlow<List<MangaSource>>(emptyList())
    override fun getSources(): Flow<List<MangaSource>> = _sources.asStateFlow()

    // Cache for manga pages to avoid repeated network calls
    private val popularMangaCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, MangaPage>>()
    private val latestMangaCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, MangaPage>>()
    private val searchCache = ConcurrentHashMap<String, ConcurrentHashMap<Pair<String, Int>, MangaPage>>()

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Load all installed extensions on initialization
        initScope.launch { refreshSources() }
    }

    override suspend fun getSource(sourceId: String): MangaSource? {
        return _sources.value.find { it.id == sourceId }
    }

    /**
     * Helper to perform a source health check and return a failure Result when unhealthy.
     * Returns null when the source is healthy so the caller can proceed.
     */
    private fun <T> failIfUnhealthy(sourceId: String): Result<T>? {
        if (!healthMonitor.isSourceHealthy(sourceId)) {
            val message = healthMonitor.getHealthMessage(sourceId)
                ?: "Source is temporarily unavailable"
            return Result.failure(IllegalStateException(message))
        }
        return null
    }

    override suspend fun getPopularManga(sourceId: String, page: Int): Result<MangaPage> {
        return withContext(Dispatchers.IO) {
            // Check source health before attempting request; still allow cached data
            if (!healthMonitor.isSourceHealthy(sourceId)) {
                popularMangaCache[sourceId]?.get(page)?.let {
                    return@withContext Result.success(it)
                }
                val message = healthMonitor.getHealthMessage(sourceId) ?: "Source is temporarily unavailable"
                return@withContext Result.failure(IllegalStateException(message))
            }

            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Source not found: $sourceId"))

                // Check cache first
                popularMangaCache[sourceId]?.get(page)?.let {
                    return@withContext Result.success(it)
                }

                val mangaPage = source.fetchPopularManga(page)

                // Cache the result
                popularMangaCache.computeIfAbsent(sourceId) { ConcurrentHashMap() }[page] = mangaPage

                // Record success
                healthMonitor.recordSuccess(sourceId)

                Result.success(mangaPage)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Record failure for health monitoring
                healthMonitor.recordFailure(sourceId, e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getLatestUpdates(sourceId: String, page: Int): Result<MangaPage> {
        return withContext(Dispatchers.IO) {
            // Check source health before attempting request; still allow cached data
            if (!healthMonitor.isSourceHealthy(sourceId)) {
                latestMangaCache[sourceId]?.get(page)?.let {
                    return@withContext Result.success(it)
                }
                val message = healthMonitor.getHealthMessage(sourceId) ?: "Source is temporarily unavailable"
                return@withContext Result.failure(IllegalStateException(message))
            }

            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Source not found: $sourceId"))

                // Check cache first
                latestMangaCache[sourceId]?.get(page)?.let {
                    return@withContext Result.success(it)
                }

                val mangaPage = source.fetchLatestUpdates(page)

                // Cache the result
                latestMangaCache.computeIfAbsent(sourceId) { ConcurrentHashMap() }[page] = mangaPage

                // Record success
                healthMonitor.recordSuccess(sourceId)

                Result.success(mangaPage)
            } catch (e: Exception) {
                // Record failure for health monitoring
                healthMonitor.recordFailure(sourceId, e)
                Result.failure(e)
            }
        }
    }

    override suspend fun searchManga(sourceId: String, query: String, page: Int): Result<MangaPage> {
        return searchManga(sourceId, query, page, FilterList())
    }

    override suspend fun searchManga(
        sourceId: String,
        query: String,
        page: Int,
        filters: FilterList
    ): Result<MangaPage> {
        return withContext(Dispatchers.IO) {
            // Check source health before attempting request
            if (!healthMonitor.isSourceHealthy(sourceId)) {
                val message = healthMonitor.getHealthMessage(sourceId) ?: "Source is temporarily unavailable"
                return@withContext Result.failure(IllegalStateException(message))
            }

            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Source not found: $sourceId"))

                val filtersAreActive = filters.hasActiveFilters()

                // Use cache when no filters are active (all at defaults)
                if (!filtersAreActive) {
                    val cacheKey = query to page
                    searchCache[sourceId]?.get(cacheKey)?.let {
                        return@withContext Result.success(it)
                    }
                }

                val mangaPage = source.fetchSearchManga(
                    page = page,
                    query = query,
                    filters = filters
                )

                // Cache only when no filters are active
                if (!filtersAreActive) {
                    val cacheKey = query to page
                    searchCache.computeIfAbsent(sourceId) { ConcurrentHashMap() }[cacheKey] = mangaPage
                }

                // Record success
                healthMonitor.recordSuccess(sourceId)

                Result.success(mangaPage)
            } catch (e: Exception) {
                // Record failure for health monitoring
                healthMonitor.recordFailure(sourceId, e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getSourceFilters(sourceId: String): FilterList {
        val source = getSource(sourceId) ?: return FilterList()
        return source.getFilterList()
    }

    override suspend fun getMangaDetails(sourceId: String, manga: SourceManga): Result<SourceManga> {
        return withContext(Dispatchers.IO) {
            // Check source health before attempting request
            if (!healthMonitor.isSourceHealthy(sourceId)) {
                val message = healthMonitor.getHealthMessage(sourceId) ?: "Source is temporarily unavailable"
                return@withContext Result.failure(IllegalStateException(message))
            }

            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Source not found: $sourceId"))

                val details = source.fetchMangaDetails(manga)

                // Record success
                healthMonitor.recordSuccess(sourceId)

                Result.success(details)
            } catch (e: Exception) {
                // Record failure for health monitoring
                healthMonitor.recordFailure(sourceId, e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getChapterList(sourceId: String, manga: SourceManga): Result<List<SourceChapter>> {
        return withContext(Dispatchers.IO) {
            // Check source health before attempting request
            if (!healthMonitor.isSourceHealthy(sourceId)) {
                val message = healthMonitor.getHealthMessage(sourceId) ?: "Source is temporarily unavailable"
                return@withContext Result.failure(IllegalStateException(message))
            }

            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Source not found: $sourceId"))

                val chapters = source.fetchChapterList(manga)

                // Record success
                healthMonitor.recordSuccess(sourceId)

                Result.success(chapters)
            } catch (e: Exception) {
                // Record failure for health monitoring
                healthMonitor.recordFailure(sourceId, e)
                Result.failure(e)
            }
        }
    }

    override suspend fun loadExtension(apkPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val extension = extensionLoader.loadExtensionFromApk(apkPath)
                    ?: return@withContext Result.failure(IllegalArgumentException("Failed to load extension from $apkPath"))

                // Add the new sources to our list
                val currentSources = _sources.value.toMutableList()
                currentSources.addAll(extension.sources)
                _sources.value = currentSources.distinctBy { it.id }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun loadExtensionFromUrl(url: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Download the APK to a temporary file
                val tempFile = File(context.cacheDir, "extension_${System.currentTimeMillis()}.apk")

                URL(url).openStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Load the extension from the downloaded file
                val result = loadExtension(tempFile.absolutePath)

                // Clean up the temporary file
                tempFile.delete()

                result
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun refreshSources() {
        withContext(Dispatchers.IO) {
            // Resolve the local source outside try/catch so errors reading preferences
            // don't get swallowed — and so the catch block can safely reference it.
            val local = try {
                currentLocalSource()
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext
            }
            try {
                // Load all installed Tachiyomi extensions
                val extensions = extensionLoader.loadAllExtensions()
                val extensionSources = extensions.flatMap { it.sources }
                // Always prepend the built-in local source
                _sources.value = (listOf(local) + extensionSources).distinctBy { it.id }
            } catch (e: Exception) {
                // Log error but don't crash; still expose the local source
                e.printStackTrace()
                _sources.value = listOf(local)
            }
        }
    }

    /**
     * Clear all caches
     */
    fun clearCaches() {
        popularMangaCache.clear()
        latestMangaCache.clear()
        searchCache.clear()
    }

    /**
     * Clear cache for a specific source
     */
    fun clearSourceCache(sourceId: String) {
        popularMangaCache.remove(sourceId)
        latestMangaCache.remove(sourceId)
        searchCache.remove(sourceId)
    }
}
