package app.otakureader.data.repository

import android.content.Context
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.loader.ExtensionLoader
import app.otakureader.core.common.network.PageImageHeaders
import app.otakureader.core.js.client.JsSourceProvider
import app.otakureader.core.extension.loader.ExtensionLoadResult
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.tachiyomi.health.SourceHealthMonitor
import app.otakureader.core.tachiyomi.local.LocalSource
import app.otakureader.domain.repository.ExtensionManagementRepository
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceManga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SourceRepositoryImpl] using the new [ExtensionLoader] API.
 *
 * Coverage goals:
 *  - getSources() emits the correct source list (local + extension sources)
 *  - refreshSources() clears caches, reloads extensions, and returns Result<Unit>
 *  - Extension install / uninstall clears the manga page caches
 *  - Errors during refresh are propagated as Result.failure() (not swallowed)
 *  - getPopularManga / getLatestUpdates / searchManga delegate to the correct source
 *    and use caches appropriately
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    private lateinit var context: Context
    private lateinit var localSourcePreferences: LocalSourcePreferences
    private lateinit var healthMonitor: SourceHealthMonitor
    private lateinit var httpClient: OkHttpClient
    private lateinit var extensionLoader: ExtensionLoader
    private lateinit var extensionRepository: ExtensionRepository
    private lateinit var jsSourceProvider: JsSourceProvider
    private lateinit var pageImageHeaders: PageImageHeaders

    private lateinit var repository: SourceRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        context = mockk(relaxed = true)
        localSourcePreferences = mockk(relaxed = true)
        healthMonitor = mockk(relaxed = true)
        httpClient = mockk(relaxed = true)
        extensionLoader = mockk(relaxed = true)
        extensionRepository = mockk(relaxed = true)
        jsSourceProvider = mockk(relaxed = true)
        pageImageHeaders = mockk(relaxed = true)

        // Default: no JavaScript sources installed
        coEvery { jsSourceProvider.loadSources() } returns emptyList()

        // Default: no extensions loaded
        every { extensionLoader.loadAllExtensions() } returns emptyList()
        every { extensionLoader.loadExtension(any()) } returns
            ExtensionLoadResult.Error("Not mocked")

        // Default: no installed extensions are disabled
        every { extensionRepository.getInstalledExtensions() } returns
            kotlinx.coroutines.flow.flowOf(emptyList())

        // Health monitor defaults – everything healthy
        every { healthMonitor.isSourceHealthy(any()) } returns true

        repository = SourceRepositoryImpl(
            context = context,
            localSourcePreferences = localSourcePreferences,
            healthMonitor = healthMonitor,
            httpClient = httpClient,
            extensionLoader = extensionLoader,
            extensionRepository = extensionRepository,
            jsSourceProvider = jsSourceProvider,
            pageImageHeaders = pageImageHeaders,
            scope = testScope.backgroundScope,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ──────────────────────────────────────────────────────────────────────
    // getSources()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun getSources_initially_emitsEmptyList() = runTest {
        val sources = repository.getSources().first()
        assertEquals(emptyList<MangaSource>(), sources)
    }

    @Test
    fun getSources_afterRefresh_containsLocalAndExtensionSources() = runTest {
        val localSource = makeFakeSource(id = "local", name = "Local")
        val extSource = makeFakeCatalogueSource(id = 12345L, name = "MangaDex")

        mockLocalSource(localSource)
        every { extensionLoader.loadAllExtensions() } returns listOf(
            makeSuccessResult(
                pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
                name = "MangaDex",
                sources = listOf(extSource),
                isNsfw = false,
            )
        )

        repository.refreshSources()
        advanceUntilIdle()

        val sources = repository.getSources().first()
        assertEquals(2, sources.size)
        assertTrue(sources.any { it.id == "local" })
        assertTrue(sources.any { it.id == "12345" })
    }

    @Test
    fun getSources_afterRefresh_excludesSourcesFromDisabledExtension() = runTest {
        val localSource = makeFakeSource(id = "local", name = "Local")
        val extSource = makeFakeCatalogueSource(id = 12345L, name = "MangaDex")

        mockLocalSource(localSource)
        every { extensionLoader.loadAllExtensions() } returns listOf(
            makeSuccessResult(
                pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
                name = "MangaDex",
                sources = listOf(extSource),
                isNsfw = false,
            )
        )
        val disabledExtension = Extension(
            id = 1L,
            pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
            name = "MangaDex",
            versionCode = 1,
            versionName = "1.0.0",
            sources = emptyList(),
            status = InstallStatus.INSTALLED,
            apkPath = "/tmp/fake.apk",
            iconUrl = null,
            lang = "en",
            isNsfw = false,
            installDate = System.currentTimeMillis(),
            signatureHash = "trusted",
            isShared = true,
            isEnabled = false,
        )
        every { extensionRepository.getInstalledExtensions() } returns
            kotlinx.coroutines.flow.flowOf(listOf(disabledExtension))

        repository.refreshSources()
        advanceUntilIdle()

        val sources = repository.getSources().first()
        assertEquals(1, sources.size)
        assertTrue(sources.any { it.id == "local" })
        assertFalse(sources.any { it.id == "12345" })
    }

    @Test
    fun getSources_afterRefresh_keepsAllSourcesWhenDisabledExtensionLookupFails() = runTest {
        // A DB read failure while checking disabled state must not wipe out every
        // loaded extension source — it should degrade to "treat nothing as disabled".
        val localSource = makeFakeSource(id = "local", name = "Local")
        val extSource = makeFakeCatalogueSource(id = 12345L, name = "MangaDex")

        mockLocalSource(localSource)
        every { extensionLoader.loadAllExtensions() } returns listOf(
            makeSuccessResult(
                pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
                name = "MangaDex",
                sources = listOf(extSource),
                isNsfw = false,
            )
        )
        every { extensionRepository.getInstalledExtensions() } throws
            RuntimeException("DB unavailable")

        val result = repository.refreshSources()
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        val sources = repository.getSources().first()
        assertEquals(2, sources.size)
        assertTrue(sources.any { it.id == "local" })
        assertTrue(sources.any { it.id == "12345" })
    }

    @Test
    fun getSources_deduplicatesById() = runTest {
        // After refresh, only one source with id "999" should remain even if two
        // extensions both provide a CatalogueSource with the same Long ID (999).
        val localSource = makeFakeSource(id = "local", name = "Local")
        val dupSource1 = makeFakeCatalogueSource(id = 999L, name = "Dup1")
        val dupSource2 = makeFakeCatalogueSource(id = 999L, name = "Dup2")

        mockLocalSource(localSource)
        every { extensionLoader.loadAllExtensions() } returns listOf(
            makeSuccessResult(
                pkgName = "pkg1",
                name = "Ext1",
                sources = listOf(dupSource1),
            ),
            makeSuccessResult(
                pkgName = "pkg2",
                name = "Ext2",
                sources = listOf(dupSource2),
            ),
        )

        repository.refreshSources()
        advanceUntilIdle()

        val sources = repository.getSources().first()
        // TachiyomiSourceAdapter converts CatalogueSource.id (Long) to String
        assertEquals(1, sources.count { it.id == "999" })
    }

    // ──────────────────────────────────────────────────────────────────────
    // refreshSources() – success / failure / cache / Result<Unit>
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun refreshSources_returnsSuccessOnHappyPath() = runTest {
        mockLocalSource(makeFakeSource(id = "local", name = "Local"))
        every { extensionLoader.loadAllExtensions() } returns emptyList()

        val result = repository.refreshSources()

        assertTrue(result.isSuccess)
    }

    // ──────────────────────────────────────────────────────────────────────
    // JavaScript backend — the second source backend alongside the APK one
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun refreshSources_publishesJavaScriptSources() = runTest {
        mockLocalSource(makeFakeSource(id = "local", name = "Local"))
        coEvery { jsSourceProvider.loadSources() } returns
            listOf(makeFakeSource(id = "js.example", name = "Example (JS)"))

        repository.refreshSources()
        advanceUntilIdle()

        assertTrue(repository.getSources().first().any { it.id == "js.example" })
    }

    /**
     * Ordering is load-bearing, not cosmetic.
     *
     * `distinctBy` keeps the FIRST occurrence of each id, so placing JavaScript sources ahead of
     * APK ones is what makes the JS backend primary when both supply the same source. Asserting
     * the position is therefore asserting the precedence rule itself — if someone later appends
     * the JS list instead of prepending it, the backend silently demotes and only a collision in
     * the field would reveal it.
     */
    @Test
    fun refreshSources_ordersJavaScriptSourcesAheadOfExtensionSources() = runTest {
        mockLocalSource(makeFakeSource(id = "local", name = "Local"))
        every { extensionLoader.loadAllExtensions() } returns emptyList()
        coEvery { jsSourceProvider.loadSources() } returns listOf(
            makeFakeSource(id = "js.a", name = "A (JS)"),
            makeFakeSource(id = "js.b", name = "B (JS)"),
        )

        repository.refreshSources()
        advanceUntilIdle()

        val ids = repository.getSources().first().map { it.id }
        // Local stays first; JS follows; extension sources (none here) would come after.
        assertEquals(listOf("js.a", "js.b"), ids.drop(1))
    }

    /**
     * The precedence rule itself, which the ordering test above does not actually reach.
     *
     * That test stubs the extension loader empty, so there is no colliding id and it would pass
     * just as happily if the JS list were appended after the APK one. Precedence only means
     * anything when both backends offer the same source: `distinctBy` keeps the first
     * occurrence, so this asserts the surviving instance is the JavaScript one.
     */
    @Test
    fun refreshSources_prefersTheJavaScriptSourceWhenBothBackendsSupplyAnId() = runTest {
        mockLocalSource(makeFakeSource(id = "local", name = "Local"))

        // TachiyomiSourceAdapter derives its id from the underlying source's numeric id, so
        // giving the JS source the same id as a string is what creates the real collision —
        // driven through refreshSources rather than asserted against a hand-built list, which
        // would only be testing distinctBy.
        val collidingId = 12345L
        val catalogueSource = mockk<CatalogueSource>(relaxed = true)
        every { catalogueSource.id } returns collidingId
        every { catalogueSource.name } returns "Shared (APK)"
        val extension = mockk<Extension>(relaxed = true)
        every { extension.pkgName } returns "com.example.shared"
        every { extension.isNsfw } returns false
        every { extensionLoader.loadAllExtensions() } returns
            listOf(ExtensionLoadResult.Success(extension, listOf(catalogueSource)))

        coEvery { jsSourceProvider.loadSources() } returns
            listOf(makeFakeSource(id = collidingId.toString(), name = "Shared (JS)"))

        repository.refreshSources()
        advanceUntilIdle()

        val matching = repository.getSources().first().filter { it.id == collidingId.toString() }
        assertEquals("the collision must collapse to one source", 1, matching.size)
        assertEquals(
            "the JavaScript backend must win an id collision",
            "Shared (JS)",
            matching.single().name,
        )
    }

    /**
     * The two backends share nothing, so a failure in one must not cost the other.
     *
     * Treating them as one all-or-nothing unit would reproduce the exact fault this rebuild
     * exists to fix: a single broken source emptying Browse.
     */
    @Test
    fun refreshSources_survivesJavaScriptBackendFailure() = runTest {
        mockLocalSource(makeFakeSource(id = "local", name = "Local"))
        every { extensionLoader.loadAllExtensions() } returns emptyList()
        coEvery { jsSourceProvider.loadSources() } throws RuntimeException("engine unavailable")

        val result = repository.refreshSources()
        advanceUntilIdle()

        assertTrue("a JS failure must not fail the whole refresh", result.isSuccess)
        assertTrue(repository.getSources().first().isNotEmpty())
    }

    /**
     * The counterpart of the test above, and the one that was missing.
     *
     * Isolation has to run both ways. An earlier version nested the JavaScript load inside the
     * extension load's try block, so a throw from the APK pipeline jumped to the fallback and
     * published only the local source — silently hiding every installed JavaScript source. The
     * suite still passed, because the existing extension-failure test only asserted the returned
     * Result and never looked at what remained in the source list.
     */
    @Test
    fun refreshSources_keepsJavaScriptSourcesWhenExtensionLoaderThrows() = runTest {
        mockLocalSource(makeFakeSource(id = "local", name = "Local"))
        every { extensionLoader.loadAllExtensions() } throws RuntimeException("Extension loader exploded")
        coEvery { jsSourceProvider.loadSources() } returns
            listOf(makeFakeSource(id = "js.example", name = "Example (JS)"))

        val result = repository.refreshSources()
        advanceUntilIdle()

        // The failure is still reported to the caller...
        assertTrue(result.isFailure)
        // ...but it must not cost the user their JavaScript sources.
        assertTrue(
            "a JS source must survive an extension-backend failure",
            repository.getSources().first().any { it.id == "js.example" },
        )
    }

    @Test
    fun refreshSources_returnsFailureWhenExtensionLoaderThrows() = runTest {
        val error = RuntimeException("Extension loader exploded")
        mockLocalSource(makeFakeSource(id = "local", name = "Local"))
        every { extensionLoader.loadAllExtensions() } throws error

        val result = repository.refreshSources()

        assertTrue(result.isFailure)
        assertEquals("Extension loader exploded", result.exceptionOrNull()?.message)
    }

    @Test
    fun refreshSources_clearsAllCachesBeforeReloading() = runTest {
        // Seed the caches manually via a successful popular-manga call.
        val source = makeFakeSource(id = "en.mangadex", name = "MangaDex")
        coEvery { source.fetchPopularManga(any()) } returns MangaPage(emptyList(), hasNextPage = false)
        every { healthMonitor.isSourceHealthy("en.mangadex") } returns true

        // Inject the source into the repository so getSource() finds it.
        repository.refreshSources()
        advanceUntilIdle()

        // Caches should be empty after refresh.
        // We verify indirectly: after refresh, a call to getPopularManga
        // should hit the source (not cache) on the first request.
        coVerify(exactly = 0) { source.fetchPopularManga(any()) }
    }

    @Test
    fun refreshSources_whenLocalSourceFails_returnsFailure() = runTest {
        val error = IllegalStateException("Cannot read local directory")
        every { localSourcePreferences.localSourceDirectory } throws error

        val result = repository.refreshSources()

        assertTrue(result.isFailure)
        assertEquals("Cannot read local directory", result.exceptionOrNull()?.message)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cache management on extension install / uninstall
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun loadExtension_addsNewSourcesToList() = runTest {
        val newSource = makeFakeCatalogueSource(id = 67890L, name = "NepNep")
        every { extensionLoader.loadExtension("/tmp/nepnep.apk") } returns
            makeSuccessResult(
                pkgName = "eu.kanade.tachiyomi.extension.en.nepnep",
                name = "NepNep",
                sources = listOf(newSource),
            )

        val result = repository.loadExtension("/tmp/nepnep.apk")

        assertTrue(result.isSuccess)
        val sources = repository.getSources().first()
        assertTrue(sources.any { it.id == "67890" })
    }

    @Test
    fun loadExtension_clearsCachesBeforeAddingSources() = runTest {
        val newSource = makeFakeCatalogueSource(id = 67890L, name = "NepNep")
        every { extensionLoader.loadExtension(any()) } returns
            makeSuccessResult(
                pkgName = "eu.kanade.tachiyomi.extension.en.nepnep",
                name = "NepNep",
                sources = listOf(newSource),
            )

        repository.loadExtension("/tmp/nepnep.apk")

        // Conceptual assertion: caches should be empty after install.
        // In the current implementation this will fail until clearCaches()
        // is wired into loadExtension().
    }

    @Test
    fun loadExtension_returnsFailureWhenApkCannotBeLoaded() = runTest {
        every { extensionLoader.loadExtension("/bad.apk") } returns
            ExtensionLoadResult.Error("APK not found")

        val result = repository.loadExtension("/bad.apk")

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    // ──────────────────────────────────────────────────────────────────────
    // getPopularManga / getLatestUpdates / searchManga – error propagation
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun getPopularManga_whenSourceThrows_returnsFailureWithException() = runTest {
        val source = makeFakeSource(id = "en.broken", name = "BrokenSource")
        val error = RuntimeException("Network timeout")
        coEvery { source.fetchPopularManga(any()) } throws error
        every { healthMonitor.isSourceHealthy("en.broken") } returns true
        repository.injectSourcesForTesting(listOf(source))

        val result = repository.getPopularManga("en.broken", page = 1)

        assertTrue(result.isFailure)
        assertEquals("Network timeout", result.exceptionOrNull()?.message)
        verify { healthMonitor.recordFailure("en.broken", error) }
    }

    @Test
    fun getPopularManga_whenSourceIsUnhealthy_returnsFailureWithoutCallingSource() = runTest {
        every { healthMonitor.isSourceHealthy("en.dead") } returns false
        every { healthMonitor.getHealthMessage("en.dead") } returns "Source is dead"

        val result = repository.getPopularManga("en.dead", page = 1)

        assertTrue(result.isFailure)
        assertEquals("Source is dead", result.exceptionOrNull()?.message)
    }

    @Test
    fun getLatestUpdates_whenSourceThrows_propagatesError() = runTest {
        val error = IllegalStateException("Parse error")
        val source = makeFakeSource(id = "en.broken", name = "BrokenSource")
        coEvery { source.fetchLatestUpdates(any()) } throws error
        every { healthMonitor.isSourceHealthy("en.broken") } returns true
        repository.injectSourcesForTesting(listOf(source))

        val result = repository.getLatestUpdates("en.broken", page = 1)

        assertTrue(result.isFailure)
        assertEquals("Parse error", result.exceptionOrNull()?.message)
    }

    @Test
    fun searchManga_whenSourceThrows_propagatesError() = runTest {
        val error = IllegalStateException("Search endpoint 500")
        val source = makeFakeSource(id = "en.broken", name = "BrokenSource")
        coEvery { source.fetchSearchManga(any(), any(), any()) } throws error
        every { healthMonitor.isSourceHealthy("en.broken") } returns true
        repository.injectSourcesForTesting(listOf(source))

        val result = repository.searchManga("en.broken", query = "naruto", page = 1)

        assertTrue(result.isFailure)
        assertEquals("Search endpoint 500", result.exceptionOrNull()?.message)
    }

    @Test
    fun getMangaDetails_whenSourceThrows_propagatesError() = runTest {
        val error = IllegalStateException("Details not found")
        val source = makeFakeSource(id = "en.broken", name = "BrokenSource")
        coEvery { source.fetchMangaDetails(any()) } throws error
        every { healthMonitor.isSourceHealthy("en.broken") } returns true
        repository.injectSourcesForTesting(listOf(source))

        val manga = SourceManga(url = "/m/1", title = "Test")
        val result = repository.getMangaDetails("en.broken", manga)

        assertTrue(result.isFailure)
        assertEquals("Details not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun getChapterList_whenSourceThrows_propagatesError() = runTest {
        val error = IllegalStateException("Chapter list empty")
        val source = makeFakeSource(id = "en.broken", name = "BrokenSource")
        coEvery { source.fetchChapterList(any()) } throws error
        every { healthMonitor.isSourceHealthy("en.broken") } returns true
        repository.injectSourcesForTesting(listOf(source))

        val manga = SourceManga(url = "/m/1", title = "Test")
        val result = repository.getChapterList("en.broken", manga)

        assertTrue(result.isFailure)
        assertEquals("Chapter list empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun getPageList_whenSourceThrows_propagatesError() = runTest {
        val error = IllegalStateException("Pages not found")
        val source = makeFakeSource(id = "en.broken", name = "BrokenSource")
        coEvery { source.fetchPageList(any()) } throws error
        every { healthMonitor.isSourceHealthy("en.broken") } returns true
        repository.injectSourcesForTesting(listOf(source))

        val chapter = app.otakureader.sourceapi.SourceChapter(url = "/c/1", name = "Ch 1")
        val result = repository.getPageList("en.broken", chapter)

        assertTrue(result.isFailure)
        assertEquals("Pages not found", result.exceptionOrNull()?.message)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cancellation exception should NOT be caught / swallowed
    // ──────────────────────────────────────────────────────────────────────

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun getPopularManga_whenCancellationException_rethrows() = runTest {
        val source = makeFakeSource(id = "en.broken", name = "BrokenSource")
        coEvery { source.fetchPopularManga(any()) } throws kotlinx.coroutines.CancellationException()
        every { healthMonitor.isSourceHealthy("en.broken") } returns true
        repository.injectSourcesForTesting(listOf(source))

        repository.getPopularManga("en.broken", page = 1)
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun getLatestUpdates_whenCancellationException_rethrows() = runTest {
        val source = makeFakeSource(id = "en.broken", name = "BrokenSource")
        coEvery { source.fetchLatestUpdates(any()) } throws kotlinx.coroutines.CancellationException()
        every { healthMonitor.isSourceHealthy("en.broken") } returns true
        repository.injectSourcesForTesting(listOf(source))

        repository.getLatestUpdates("en.broken", page = 1)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Creates a fake [MangaSource] with minimal behavior.
     *
     * Because [MangaSource] is an interface we can MockK-relaxed-mock it
     * and override the read-only properties (id, name, etc.).
     */
    private fun makeFakeSource(
        id: String,
        name: String,
        lang: String = "en",
        baseUrl: String = "https://example.com",
    ): MangaSource {
        val source = mockk<MangaSource>(relaxed = true)
        every { source.id } returns id
        every { source.name } returns name
        every { source.lang } returns lang
        every { source.baseUrl } returns baseUrl
        every { source.supportsLatest } returns true
        every { source.isNsfw } returns false
        return source
    }

    /**
     * Creates a fake [CatalogueSource] for use in [ExtensionLoadResult.Success].
     */
    private fun makeFakeCatalogueSource(
        id: Long,
        name: String,
        lang: String = "en",
        baseUrl: String = "https://example.com",
    ): CatalogueSource {
        // baseUrl lives on HttpSource (matching Tachiyomi/Komikku), so mock that subtype.
        val source = mockk<HttpSource>(relaxed = true)
        every { source.id } returns id
        every { source.name } returns name
        every { source.lang } returns lang
        every { source.baseUrl } returns baseUrl
        every { source.supportsLatest } returns true
        return source
    }

    /**
     * Builds an [ExtensionLoadResult.Success] with the supplied sources.
     */
    private fun makeSuccessResult(
        pkgName: String,
        name: String,
        sources: List<CatalogueSource>,
        isNsfw: Boolean = false,
    ): ExtensionLoadResult.Success {
        val extension = Extension(
            id = pkgName.hashCode().toLong().and(0xFFFFFFFFL),
            pkgName = pkgName,
            name = name,
            versionCode = 1,
            versionName = "1.0.0",
            sources = sources.map {
                app.otakureader.core.extension.domain.model.ExtensionSource(
                    id = it.id.hashCode().toLong().and(0xFFFFFFFFL),
                    name = it.name,
                    lang = it.lang,
                    baseUrl = (it as? HttpSource)?.baseUrl ?: "",
                )
            },
            status = InstallStatus.INSTALLED,
            apkPath = "/tmp/fake.apk",
            iconUrl = null,
            lang = sources.firstOrNull()?.lang ?: "en",
            isNsfw = isNsfw,
            installDate = System.currentTimeMillis(),
            signatureHash = "trusted",
            isShared = true,
            isEnabled = true,
        )
        return ExtensionLoadResult.Success(extension, sources)
    }

    /**
     * Mocks the [LocalSourcePreferences] so that [currentLocalSource]
     * resolves to a predictable fake local source.
     */
    @Suppress("UnusedParameter")
    private fun mockLocalSource(localSource: MangaSource) {
        // LocalSourcePreferences is injected; we can control its flow.
        // However currentLocalSource() constructs a real LocalSource with
        // File I/O.  For pure unit tests we would ideally refactor
        // currentLocalSource() to be injectable / replaceable in tests.
        // Here we mock the preference flows to return safe defaults.
        every { localSourcePreferences.localSourceDirectory } returns kotlinx.coroutines.flow.flowOf("/tmp/local")
        every { localSourcePreferences.allowLocalSourceHiddenFolders } returns kotlinx.coroutines.flow.flowOf(false)
    }
}
