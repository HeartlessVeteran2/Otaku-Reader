package app.otakureader.core.tachiyomi.repository

import android.content.Context
import app.otakureader.core.tachiyomi.compat.TachiyomiExtensionLoader
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceManga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of SourceRepository using Tachiyomi extension adapters.
 */
class SourceRepositoryImpl(
    private val context: Context
) : SourceRepository {

    private val extensionLoader = TachiyomiExtensionLoader(
        context.packageManager,
        context.cacheDir
    )

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

    override suspend fun getPopularManga(sourceId: String, page: Int): Result<MangaPage> {
        return withContext(Dispatchers.IO) {
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

                Result.success(mangaPage)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getLatestUpdates(sourceId: String, page: Int): Result<MangaPage> {
        return withContext(Dispatchers.IO) {
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

                Result.success(mangaPage)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun searchManga(sourceId: String, query: String, page: Int): Result<MangaPage> {
        return withContext(Dispatchers.IO) {
            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Source not found: $sourceId"))

                // Check cache first
                val cacheKey = query to page
                searchCache[sourceId]?.get(cacheKey)?.let {
                    return@withContext Result.success(it)
                }

                val mangaPage = source.fetchSearchManga(
                    page = page,
                    query = query,
                    filters = app.otakureader.sourceapi.FilterList()
                )

                // Cache the result
                searchCache.computeIfAbsent(sourceId) { ConcurrentHashMap() }[cacheKey] = mangaPage

                Result.success(mangaPage)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getMangaDetails(sourceId: String, manga: SourceManga): Result<SourceManga> {
        return withContext(Dispatchers.IO) {
            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Source not found: $sourceId"))

                val details = source.fetchMangaDetails(manga)
                Result.success(details)
            } catch (e: Exception) {
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
            try {
                // Load all installed Tachiyomi extensions
                val extensions = extensionLoader.loadAllExtensions()
                val sources = extensions.flatMap { it.sources }
                _sources.value = sources.distinctBy { it.id }
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
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
