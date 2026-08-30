package app.otakureader.data.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.loader.ExtensionLoader
import app.otakureader.core.extension.loader.ExtensionLoadResult
import app.otakureader.core.js.client.JsSourceProvider
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.tachiyomi.compat.TachiyomiSourceAdapter
import app.otakureader.core.tachiyomi.health.SourceHealthMonitor
import app.otakureader.core.tachiyomi.local.LocalSource
import app.otakureader.domain.repository.ExtensionManagementRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.associateBySourceKey
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import app.otakureader.core.common.di.ApplicationScope
import app.otakureader.core.common.network.PageImageHeaders
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.otakureader.core.common.net.await
import okhttp3.OkHttpClient
import okhttp3.Request
import eu.kanade.tachiyomi.source.CatalogueSource
import java.io.File
import java.io.InterruptedIOException
import app.otakureader.core.common.collection.BoundedCache
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
    @param:ApplicationContext private val context: Context,
    private val localSourcePreferences: LocalSourcePreferences,
    private val healthMonitor: SourceHealthMonitor,
    private val httpClient: OkHttpClient,
    private val extensionLoader: ExtensionLoader,
    private val extensionRepository: ExtensionRepository,
    private val jsSourceProvider: JsSourceProvider,
    private val pageImageHeaders: PageImageHeaders,
    @param:ApplicationScope private val scope: CoroutineScope,
) : SourceRepository, ExtensionManagementRepository {

    private companion object {
        const val TAG = "SourceRepositoryImpl"

        /**
         * Browse pages kept per source.
         *
         * Sized for how a source is actually read — a user scrolls a handful of pages deep and
         * then either opens something or moves on — with enough slack that paging back up is
         * still a cache hit rather than a refetch.
         */
        const val MAX_CACHED_PAGES_PER_SOURCE = 32

        /**
         * Search results kept per source, keyed by (query, page).
         *
         * Lower than the page cap because the key space is unbounded: every distinct string a
         * user types is a new entry, so this is the one that grew without limit before.
         */
        const val MAX_CACHED_SEARCHES_PER_SOURCE = 16

        /**
         * How long a source lookup waits for the initial load before giving up (#1258).
         *
         * Generous, because the cost of expiring early is the bug this exists to fix, while the
         * cost of waiting is a one-off delay on the very first lookup after process start. It is
         * bounded at all only so a wedged extension load cannot stall a library update forever.
         */
        const val INITIAL_LOAD_TIMEOUT_MS = 15_000L
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
        jsSourceProvider: JsSourceProvider,
        pageImageHeaders: PageImageHeaders,
        scope: CoroutineScope,
    ) : this(
        // Named rather than positional: this delegation silently mis-bound when a parameter was
        // inserted into the primary constructor, and the next insertion would do it again.
        context = context,
        localSourcePreferences = LocalSourcePreferences.ofDirectory(localDirectory),
        healthMonitor = healthMonitor,
        httpClient = httpClient,
        extensionLoader = extensionLoader,
        extensionRepository = extensionRepository,
        jsSourceProvider = jsSourceProvider,
        pageImageHeaders = pageImageHeaders,
        scope = scope,
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
        // Injecting sources *is* the loaded state, so release the readiness signal too. Without
        // this a test that injects and then resolves would sit on the timeout in awaitInitialLoad
        // instead of reading what it just injected.
        _sourcesLoading.value = false
        initialLoadComplete.complete(Unit)
    }

    /**
     * One generation of cached browse results.
     *
     * Invalidation swaps this whole object rather than clearing the maps in place, and that is
     * what makes it correct rather than merely tidy. A fetch captures the instance it started
     * against and writes its result back into *that* instance. If an invalidation lands while the
     * network call is in flight, the write goes into an object nothing reads any more, instead of
     * resurrecting pre-refresh data under a source id that has just been rebuilt.
     *
     * Clearing in place cannot express that: `remove` then `computeIfAbsent` is a read-then-act
     * pair across a suspension point, so the late write recreates the map it was supposed to
     * have been erased from. Comparing object identity removes the window entirely rather than
     * narrowing it — no lock spanning the network call, and nothing to get the ordering wrong.
     *
     * The inner maps are bounded because they were the unbounded ones. [search] worst of all:
     * keyed by (query, page), it retained a full page of results for every distinct string the
     * user had ever typed, for the lifetime of the process.
     */
    private class BrowseCaches {
        val popular = ConcurrentHashMap<String, BoundedCache<Int, MangaPage>>()
        val latest = ConcurrentHashMap<String, BoundedCache<Int, MangaPage>>()
        val search = ConcurrentHashMap<String, BoundedCache<Pair<String, Int>, MangaPage>>()
    }

    /** Volatile so a swap by [clearCaches] is visible to threads already reading it. */
    @Volatile
    private var browseCaches = BrowseCaches()

    /**
     * Completes once the [init] refresh has finished, however it finished (#1258).
     *
     * The initial load is fire-and-forget, so before this existed every resolution path read an
     * `_sources` snapshot with no notion of whether that load had happened yet. A caller that
     * arrived first got `null` and could not tell it apart from "no such source". The realistic
     * loser is `LibraryUpdateWorker`, which WorkManager can start at process launch: a miss there
     * surfaces as an update error against a manga whose extension is installed and fine.
     *
     * Three properties are load-bearing, and each rules out a shape that looks simpler:
     *
     * - **It is an explicit signal, not "await a non-empty `_sources`".** A user with no
     *   extensions installed is a legitimate steady state, and waiting for that list to fill
     *   would hang every lookup forever.
     * - **It completes in a `finally`, on success, failure, or nothing-found alike.** Awaiting a
     *   signal that a failed refresh never raises would be worse than the bug it fixes.
     * - **Awaiting it is bounded.** [awaitInitialLoad] gives up after
     *   [INITIAL_LOAD_TIMEOUT_MS] and proceeds with whatever is loaded, so a wedged extension
     *   load degrades to today's behaviour instead of stalling a library update indefinitely.
     */
    private val initialLoadComplete = CompletableDeferred<Unit>()

    /**
     * Whether the initial source load is still running.
     *
     * Exposed because Browse could not distinguish "no sources installed" from "not loaded yet"
     * and showed the same empty state for both — an empty list is a real answer only once this
     * is false.
     */
    private val _sourcesLoading = MutableStateFlow(true)
    override fun isLoadingSources(): Flow<Boolean> = _sourcesLoading.asStateFlow()

    init {
        // Load all installed extensions on initialization
        scope.launch {
            try {
                refreshSources()
            } finally {
                // In a finally, not after the call: a failed or cancelled refresh must still
                // release everything awaiting the signal.
                _sourcesLoading.value = false
                initialLoadComplete.complete(Unit)
            }
        }
    }

    /**
     * Waits, at most [INITIAL_LOAD_TIMEOUT_MS], for the initial source load to finish.
     *
     * Every resolution path awaits this rather than only the background workers that can
     * plausibly beat it. Two behaviours for the same call would be a trap for the next caller,
     * and once loading has finished this costs a single already-completed `await`.
     */
    private suspend fun awaitInitialLoad() {
        if (initialLoadComplete.isCompleted) return
        if (withTimeoutOrNull(INITIAL_LOAD_TIMEOUT_MS) { initialLoadComplete.await() } != null) return
        // Expiring is terminal, not a per-call reprieve. Completing the signal here is what stops
        // a wedged load from charging every later lookup the full timeout again — the first caller
        // pays it once and everyone after reads the snapshot immediately. Clearing the loading flag
        // with it stops Browse spinning forever on a load that is never going to finish: an empty
        // list after the timeout is the honest answer, and the user can retry from the UI.
        //
        // Safe against the load finishing later: `complete` returns false on an already-completed
        // deferred, and refreshSources publishes into `_sources` regardless of who is waiting, so a
        // late arrival still reaches every subsequent lookup.
        _sourcesLoading.value = false
        initialLoadComplete.complete(Unit)
    }

    override suspend fun getSource(sourceId: String): MangaSource? {
        awaitInitialLoad()
        return _sources.value.find { it.id == sourceId }
    }

    /**
     * Reverses [toSourceId] by searching the loaded sources, because hashing is one-way.
     *
     * Delegates to [associateBySourceKey] rather than restating the rule. That rule has two parts
     * — the canonical [toSourceId] hash, plus a legacy `toLongOrNull()` parse that
     * `SourceMangaDetailViewModel` once wrote, indistinguishable from a hash on disk and so
     * unrepairable by migration — and it is also used to index sources by key elsewhere. Written
     * out twice, the two copies agreed on every key owned by one source but disagreed on which
     * source won a collision, and nothing would have caught them drifting further apart.
     */
    override suspend fun getSourceByKey(key: Long): MangaSource? {
        awaitInitialLoad()
        return _sources.value.associateBySourceKey { it.id }[key]
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
                browseCaches.popular[sourceId]?.get(page)?.let {
                    return@withContext Result.success(it)
                }
                val message = healthMonitor.getHealthMessage(sourceId) ?: "Source is temporarily unavailable"
                return@withContext Result.failure(IllegalStateException(message))
            }

            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Source not found: $sourceId"))

                // Check cache first
                browseCaches.popular[sourceId]?.get(page)?.let {
                    return@withContext Result.success(it)
                }

                // Captured before the call: the result belongs to the generation that was
                // current when the request started, not to whatever replaced it meanwhile.
                val caches = browseCaches
                val mangaPage = source.fetchPopularManga(page)

                caches.popular.computeIfAbsent(sourceId) { BoundedCache(MAX_CACHED_PAGES_PER_SOURCE) }[page] = mangaPage

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
                browseCaches.latest[sourceId]?.get(page)?.let {
                    return@withContext Result.success(it)
                }
                val message = healthMonitor.getHealthMessage(sourceId) ?: "Source is temporarily unavailable"
                return@withContext Result.failure(IllegalStateException(message))
            }

            try {
                val source = getSource(sourceId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Source not found: $sourceId"))

                // Check cache first
                browseCaches.latest[sourceId]?.get(page)?.let {
                    return@withContext Result.success(it)
                }

                val caches = browseCaches
                val mangaPage = source.fetchLatestUpdates(page)

                caches.latest.computeIfAbsent(sourceId) { BoundedCache(MAX_CACHED_PAGES_PER_SOURCE) }[page] = mangaPage

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
                    browseCaches.search[sourceId]?.get(cacheKey)?.let {
                        return@withContext Result.success(it)
                    }
                }

                val caches = browseCaches
                val mangaPage = source.fetchSearchManga(
                    page = page,
                    query = query,
                    filters = filters
                )

                // Cache only when no filters are active
                if (!filtersAreActive) {
                    val cacheKey = query to page
                    caches.search.computeIfAbsent(sourceId) { BoundedCache(MAX_CACHED_SEARCHES_PER_SOURCE) }[cacheKey] = mangaPage
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

                // Record the Referer for these images while we still know which source produced
                // them. This is the only place that knows both, and registering here rather than
                // at the six places that request page images means a new call site cannot forget
                // — including the prefetchers, which would otherwise fetch without it and poison
                // the cache with 403s the reader then displays as blank pages.
                pageImageHeaders.registerReferer(
                    sourceBaseUrl = source.baseUrl,
                    pageUrls = pages.mapNotNull { it.imageUrl?.takeIf(String::isNotBlank) ?: it.url },
                )

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

                httpClient.newCall(request).await().use { response ->
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
            // The two backends are loaded independently, and that independence is the point.
            //
            // They share no state, so neither one's failure may remove the other's sources. An
            // earlier version nested the JavaScript load inside the extension load's try block,
            // which protected the APK sources from a JS failure but not the reverse: any throw
            // from the extension pipeline jumped straight to the fallback and published only the
            // local source, silently hiding every installed JavaScript source. That is the exact
            // fault this rebuild exists to fix, so the isolation has to run both ways.
            val jsSources = loadJsSources()
            val extensionSources = try {
                Result.success(loadExtensionSources())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to load extensions", e)
                Result.failure(e)
            }

            // JavaScript sources come before APK ones, and the order is load-bearing rather than
            // cosmetic: distinctBy keeps the FIRST occurrence of each id, so when both backends
            // supply the same source the JS one wins. That makes "JS is the primary backend" a
            // structural property of the list instead of a convention later code can quietly
            // break.
            _sources.value = (listOf(local) + jsSources + extensionSources.getOrDefault(emptyList()))
                .distinctBy { it.id }
            clearCaches()

            // Whatever loaded is published either way; the Result only reports whether the
            // extension backend had a problem worth surfacing to the caller.
            extensionSources.map { }
        }
    }

    /**
     * Load the APK-backed sources. Throws if the extension pipeline fails, so the caller can
     * decide what that means — which is "report it, but keep the other backend's sources".
     */
    private suspend fun loadExtensionSources(): List<MangaSource> {
        val results = extensionLoader.loadAllExtensions()
        // Sources are parsed fresh from the APK on every load, so ExtensionLoadResult's
        // Extension.isEnabled is always the model default (true) and never reflects the user's
        // stored preference. Cross-reference the DB-persisted flag here so a disabled
        // extension's sources stay out of Browse. A failure to read this is non-fatal — fall
        // back to treating nothing as disabled rather than losing every loaded source below.
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
        // Report why extensions were dropped before filtering them away. Discarding the
        // non-Success results silently is what made a broken extension pipeline undiagnosable:
        // when every extension failed to load, Browse showed only the local source and nothing
        // anywhere — not even logcat — said why. Grouped by reason so one bad repo produces a
        // single line rather than one per extension.
        logSkippedExtensions(results)

        return results
            .filterIsInstance<ExtensionLoadResult.Success>()
            .filterNot { it.extension.pkgName in disabledPkgNames }
            .flatMap { success ->
                success.sources
                    .filterIsInstance<CatalogueSource>()
                    .map { TachiyomiSourceAdapter(it, success.extension.isNsfw) }
            }
    }

    /**
     * Load the JavaScript-backed sources, absorbing failure.
     *
     * Returns an empty list rather than throwing, because a JS backend problem must never cost
     * the user their APK sources — the counterpart of the extension backend's failure being
     * confined to itself.
     */
    private suspend fun loadJsSources(): List<MangaSource> = try {
        jsSourceProvider.loadSources()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(TAG, "Failed to load JavaScript sources", e)
        emptyList()
    }

    /**
     * Log every extension that failed to load, grouped by reason.
     *
     * [ExtensionLoadResult.Untrusted] is reported separately from [ExtensionLoadResult.Error]
     * because the two need different user actions: untrusted extensions are recoverable from
     * the extensions screen, while an error usually means the extension is incompatible.
     */
    private fun logSkippedExtensions(results: List<ExtensionLoadResult>) {
        summarizeSkippedExtensions(results).forEach { android.util.Log.w(TAG, it) }
    }

    /**
     * Drop every cached browse result.
     *
     * Swaps the generation rather than clearing the maps: a fetch already in flight holds the old
     * instance and writes its now-stale result there, where nothing will read it.
     *
     * There is deliberately no per-source variant. One existed, was never called by anything, and
     * carried this same race in a form the swap cannot fix — removing one source's entries from
     * the live generation is an in-place edit, so an in-flight fetch for that source repopulates
     * it. Anyone adding per-source invalidation later has to solve that, and inheriting a helper
     * that looks usable and quietly is not would make it likelier they miss it.
     */
    fun clearCaches() {
        browseCaches = BrowseCaches()
    }
}

/** Cap on a sample failure message, so one pathological string cannot flood logcat. */
private const val SKIPPED_REASON_MAX_LENGTH = 200

/**
 * Build the log lines describing extensions that were dropped during a source refresh.
 *
 * Separated from the logging call so the grouping can be asserted directly — `android.util.Log`
 * is a no-op stub under unit tests, so behaviour verified only at the call site would not be
 * verified at all.
 *
 * Grouping is keyed on [ExtensionLoadResult.Error.reason], never on the message. Messages
 * interpolate the package name, so a message-keyed grouping degenerates to one line per
 * extension — the exact flood the grouping exists to prevent — and silently re-groups whenever
 * a message is reworded. One sample message per group is retained for detail.
 */
internal fun summarizeSkippedExtensions(results: List<ExtensionLoadResult>): List<String> {
    val lines = mutableListOf<String>()

    val untrusted = results.filterIsInstance<ExtensionLoadResult.Untrusted>()
    if (untrusted.isNotEmpty()) {
        lines += "${untrusted.size} extension(s) not loaded because their signature is not trusted: " +
            untrusted.joinToString { it.extension.pkgName }
    }

    results.filterIsInstance<ExtensionLoadResult.Error>()
        .groupBy { it.reason }
        .forEach { (reason, group) ->
            val sample = group.first().message.take(SKIPPED_REASON_MAX_LENGTH)
            lines += "${group.size} extension(s) failed to load [$reason] — e.g. $sample"
        }

    return lines
}
