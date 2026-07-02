package app.otakureader.data.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.loader.ExtensionLoader
import app.otakureader.core.extension.loader.ExtensionLoadResult
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.tachiyomi.compat.TachiyomiSourceAdapter
import app.otakureader.core.tachiyomi.health.SourceHealthMonitor
import app.otakureader.core.tachiyomi.local.LocalSource
import app.otakureader.domain.repository.ExtensionManagementRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import app.otakureader.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import eu.kanade.tachiyomi.source.CatalogueSource
import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SourceRepository using Tachiyomi extension adapters.
 * Also includes the built-in [LocalSource] for on-device manga.
 *
 * Integrates [SourceHealthMonitor] to track source failures and prevent
 * repeated requests to dead/failing sources (inspired by Komikku's health monitoring).
 */
@Singleton
class SourceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localSourcePreferences: LocalSourcePreferences,
    private val healthMonitor: SourceHealthMonitor,
    private val httpClient: OkHttpClient,
    private val extensionLoader: ExtensionLoader,
    private val extensionRepository: ExtensionRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : SourceRepository, ExtensionManagementRepository {

    private companion object {
        const val TAG = "SourceRepositoryImpl"
    }

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
        healthMonitor: SourceHealthMonitor,
        httpClient: OkHttpClient,
        extensionLoader: ExtensionLoader,
        extensionRepository: ExtensionRepository,
        scope: CoroutineScope,
    ) : this(
        context,
        LocalSourcePreferences.ofDirectory(localDirectory),
        healthMonitor,
        httpClient,
        extensionLoader,
        extensionRepository,
        scope,
    )

    /**
     * Returns a fresh [LocalSource] using the current scan directory from preferences.
     * Reading from the Flow is deferred to suspend call-sites so no blocking occurs at init.
     */
    private suspend fun currentLocalSource(): LocalSource {
        val dir = localSourcePreferences.localSourceDirectory.first()
        val allowHidden = localSourcePreferences.allowLocalSourceHiddenFolders.first()
        return LocalSource(context, dir, allowHiddenFolders = allowHidden)
    }

    private val _sources = MutableStateFlow<List<MangaSource>>(emptyList())
    override fun getSources(): Flow<List<MangaSource>> = _sources.asStateFlow()

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun injectSourcesForTesting(sources: List<MangaSource>) {
        _sources.value = sources
    }

    // Cache for manga pages to avoid repeated network calls
    private val popularMangaCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, MangaPage>>()
    private val latestMangaCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, MangaPage>>()
    private val searchCache = ConcurrentHashMap<String, ConcurrentHashMap<Pair<String, Int>, MangaPage>>()

    init {
        // Load all installed extensions on initialization
        scope.launch { refreshSources() }
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

    /**
     * Determines if a failure should be recorded in the health monitor.
     * Filters out cancellation and interruption exceptions which are normal
     * lifecycle events rather than source health issues.
     */
    private fun shouldRecordFailure(error: Throwable): Boolean {
        return error !is CancellationException &&
               error !is InterruptedIOException
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Record failure for health monitoring
                if (shouldRecordFailure(e)) {
                    healthMonitor.recordFailure(sourceId, e)
                }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Record failure for health monitoring
                if (shouldRecordFailure(e)) {
                    healthMonitor.recordFailure(sourceId, e)
                }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Record failure for health monitoring
                if (shouldRecordFailure(e)) {
                    healthMonitor.recordFailure(sourceId, e)
                }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Record failure for health monitoring
                if (shouldRecordFailure(e)) {
                    healthMonitor.recordFailure(sourceId, e)
                }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: InterruptedIOException) {
                Result.failure(e)
            } catch (e: Exception) {
                healthMonitor.recordFailure(sourceId, e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getPageList(
        sourceId: String,
        chapter: SourceChapter
    ): Result<List<app.otakureader.sourceapi.Page>> {
        return withContext(Dispatchers.IO) {
            failIfUnhealthy<List<app.otakureader.sourceapi.Page>>(sourceId)
                ?.let { return@withContext it }

            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Source not found: $sourceId")
                    )

                val pages = source.fetchPageList(chapter)
                healthMonitor.recordSuccess(sourceId)
                Result.success(pages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shouldRecordFailure(e)) {
                    healthMonitor.recordFailure(sourceId, e)
                }
                Result.failure(e)
            }
        }
    }

    override suspend fun loadExtension(apkPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val result = extensionLoader.loadExtension(apkPath)
                when (result) {
                    is ExtensionLoadResult.Success -> {
                        val newSources = result.sources
                            .filterIsInstance<CatalogueSource>()
                            .map { TachiyomiSourceAdapter(it, result.extension.isNsfw) }

                        val currentSources = _sources.value.toMutableList()
                        currentSources.addAll(newSources)
                        _sources.value = currentSources.distinctBy { it.id }
                        clearCaches()

                        Result.success(Unit)
                    }
                    is ExtensionLoadResult.Untrusted -> {
                        Result.failure(
                            SecurityException(
                                "Extension is not trusted: ${result.extension.name}. " +
                                    "Please add it to a trusted repository or enable sideloading."
                            )
                        )
                    }
                    is ExtensionLoadResult.Error -> {
                        Result.failure(
                            result.throwable ?: IllegalStateException(result.message)
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun loadExtensionFromUrl(url: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Download the APK to a temporary file using OkHttp with safe temp file creation
            val tempFile = File.createTempFile("extension_", ".apk", context.cacheDir)

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.android.package-archive")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Failed to download extension: HTTP ${response.code}")
                        )
                    }
                    val body = response.body
                        ?: return@withContext Result.failure(IllegalStateException("Empty response body"))

                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Load the extension from the downloaded file through ExtensionLoader
                // (includes signature verification and private extension handling)
                loadExtension(tempFile.absolutePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                // Always clean up the temporary file
                tempFile.delete()
            }
        }
    }

    override suspend fun refreshSources(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val local = try {
                currentLocalSource()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to resolve local source", e)
                return@withContext Result.failure(e)
            }
            try {
                val results = extensionLoader.loadAllExtensions()
                // Sources are parsed fresh from the APK on every load, so ExtensionLoadResult's
                // Extension.isEnabled is always the model default (true) and never reflects the
                // user's stored preference. Cross-reference the DB-persisted flag here so a
                // disabled extension's sources stay out of Browse. A failure to read this is
                // non-fatal — fall back to treating nothing as disabled rather than losing every
                // loaded source below.
                val disabledPkgNames = try {
                    extensionRepository.getInstalledExtensions().first()
                        .filterNot { it.isEnabled }
                        .map { it.pkgName }
                        .toSet()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to read disabled extensions, treating all as enabled", e)
                    emptySet()
                }
                val extensionSources = results
                    .filterIsInstance<ExtensionLoadResult.Success>()
                    .filterNot { it.extension.pkgName in disabledPkgNames }
                    .flatMap { success ->
                        success.sources
                            .filterIsInstance<CatalogueSource>()
                            .map { TachiyomiSourceAdapter(it, success.extension.isNsfw) }
                    }
                _sources.value = (listOf(local) + extensionSources).distinctBy { it.id }
                clearCaches()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to load extensions, falling back to local source", e)
                _sources.value = listOf(local)
                Result.failure(e)
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
