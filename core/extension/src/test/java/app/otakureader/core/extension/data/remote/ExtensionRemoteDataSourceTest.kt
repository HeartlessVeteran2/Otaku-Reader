package app.otakureader.core.extension.data.remote

import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExtensionRemoteDataSourceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repoRepository: ExtensionRepoRepository
    private lateinit var dataSource: ExtensionRemoteDataSourceImpl
    private lateinit var httpClient: OkHttpClient

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        repoRepository = mockk()
        httpClient = OkHttpClient.Builder().build()
        dataSource = ExtensionRemoteDataSourceImpl(repoRepository, httpClient)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchAvailableExtensions with index_min_json format`() = runTest {
        // Given: A repository URL and minified index response
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        coEvery { repoRepository.getRepositories() } returns flowOf(listOf(baseUrl))

        val minifiedExtensions = listOf(
            MinifiedExtensionDto(
                name = "Tachiyomi: MangaDex",
                pkg = "eu.kanade.tachiyomi.extension.en.mangadex",
                apk = "tachiyomi-en.mangadex-v1.2.3.apk",
                lang = "en",
                code = 15,
                version = "1.2.3",
                nsfw = 0,
                sources = listOf(
                    MinifiedExtensionSourceDto(
                        name = "MangaDex",
                        lang = "en",
                        id = "2499283573021220255",
                        baseUrl = "https://mangadex.org"
                    )
                ),
                icon = "icon/tachiyomi-en.mangadex-v1.2.3.png"
            )
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(minifiedExtensions))
        )

        // When: Fetching available extensions
        val result = dataSource.fetchAvailableExtensions()

        // Then: Extensions are parsed correctly
        assertTrue(result.isSuccess)
        val extensions = result.getOrThrow()
        assertEquals(1, extensions.size)

        val extension = extensions[0]
        assertEquals("eu.kanade.tachiyomi.extension.en.mangadex", extension.pkgName)
        assertEquals("Tachiyomi: MangaDex", extension.name)
        assertEquals(15, extension.versionCode)
        assertEquals("1.2.3", extension.versionName)
        assertEquals("en", extension.lang)
        assertEquals(false, extension.isNsfw)
        assertEquals(1, extension.sources.size)

        // Verify APK URL is resolved correctly
        val expectedApkUrl = "$baseUrl/tachiyomi-en.mangadex-v1.2.3.apk"
        assertEquals(expectedApkUrl, extension.apkUrl)

        // Verify icon URL is resolved correctly
        val expectedIconUrl = "$baseUrl/icon/tachiyomi-en.mangadex-v1.2.3.png"
        assertEquals(expectedIconUrl, extension.iconUrl)

        // Verify source is parsed correctly
        val source = extension.sources[0]
        assertEquals("MangaDex", source.name)
        assertEquals("en", source.lang)
        assertEquals("https://mangadex.org", source.baseUrl)
    }

    @Test
    fun `fetchAvailableExtensions with standard index_json format`() = runTest {
        // Given: A repository URL and standard index response
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        coEvery { repoRepository.getRepositories() } returns flowOf(listOf(baseUrl))

        val standardResponse = ExtensionRepoResponse(
            extensions = listOf(
                ExtensionDto(
                    id = 123L,
                    pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
                    name = "MangaDex",
                    versionCode = 15,
                    versionName = "1.2.3",
                    sources = listOf(
                        ExtensionSourceDto(
                            id = 2499283573021220255L,
                            name = "MangaDex",
                            lang = "en",
                            baseUrl = "https://mangadex.org",
                            supportsSearch = true,
                            supportsLatest = true
                        )
                    ),
                    apkUrl = "https://example.com/extensions/tachiyomi-en.mangadex-v1.2.3.apk",
                    iconUrl = "https://example.com/icons/mangadex.png",
                    lang = "en",
                    isNsfw = false,
                    signature = "abc123"
                )
            ),
            lastModified = 1234567890L
        )

        // Enqueue 404 for index.min.json, then 200 for index.json
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(standardResponse))
        )

        // When: Fetching available extensions
        val result = dataSource.fetchAvailableExtensions()

        // Then: Extensions are parsed correctly
        assertTrue(result.isSuccess)
        val extensions = result.getOrThrow()
        assertEquals(1, extensions.size)

        val extension = extensions[0]
        assertEquals(123L, extension.id)
        assertEquals("eu.kanade.tachiyomi.extension.en.mangadex", extension.pkgName)
        assertEquals("MangaDex", extension.name)
        assertEquals(15, extension.versionCode)
        assertEquals("1.2.3", extension.versionName)
        assertEquals("https://example.com/extensions/tachiyomi-en.mangadex-v1.2.3.apk", extension.apkUrl)
        assertEquals("abc123", extension.signatureHash)
    }

    @Test
    fun `fetchAvailableExtensions when both index endpoints fail`() = runTest {
        // Given: A repository URL where both index.min.json and index.json fail
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        coEvery { repoRepository.getRepositories() } returns flowOf(listOf(baseUrl))

        // Enqueue failures for both index.min.json and index.json
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Server Error")
        )

        // When: Fetching available extensions
        val result = dataSource.fetchAvailableExtensions()

        // Then: The failure is propagated (no extensions are returned)
        assertTrue(result.isFailure)
    }

    @Test
    fun `fetchAvailableExtensions deduplicates by package name`() = runTest {
        // Given: Two repositories with overlapping extensions
        val baseUrl1 = mockWebServer.url("/repo1/").toString().trimEnd('/')
        val baseUrl2 = mockWebServer.url("/repo2/").toString().trimEnd('/')
        coEvery { repoRepository.getRepositories() } returns flowOf(listOf(baseUrl1, baseUrl2))

        val extension1 = MinifiedExtensionDto(
            name = "Extension v1",
            pkg = "com.example.extension",
            apk = "extension-v1.apk",
            lang = "en",
            code = 1,
            version = "1.0",
            nsfw = 0,
            sources = emptyList()
        )

        val extension2 = MinifiedExtensionDto(
            name = "Extension v2",
            pkg = "com.example.extension",
            apk = "extension-v2.apk",
            lang = "en",
            code = 2,
            version = "2.0",
            nsfw = 0,
            sources = emptyList()
        )

        // First repository returns v1
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(listOf(extension1)))
        )

        // Second repository returns v2
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(listOf(extension2)))
        )

        // When: Fetching available extensions
        val result = dataSource.fetchAvailableExtensions()

        // Then: Only highest version is returned
        assertTrue(result.isSuccess)
        val extensions = result.getOrThrow()
        assertEquals(1, extensions.size)
        assertEquals(2, extensions[0].versionCode)
        assertEquals("2.0", extensions[0].versionName)
    }

    @Test
    fun `fetchAvailableExtensions returns empty list when no repositories configured`() = runTest {
        // Given: No repositories configured
        coEvery { repoRepository.getRepositories() } returns flowOf(emptyList())

        // When: Fetching available extensions
        val result = dataSource.fetchAvailableExtensions()

        // Then: Empty list is returned
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `resolves relative APK URLs correctly`() = runTest {
        // Given: A repository with relative APK path
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        coEvery { repoRepository.getRepositories() } returns flowOf(listOf(baseUrl))

        val extension = MinifiedExtensionDto(
            name = "Test Extension",
            pkg = "com.test.extension",
            apk = "apk/test-extension.apk",
            lang = "en",
            code = 1,
            version = "1.0",
            nsfw = 0,
            sources = emptyList()
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(listOf(extension)))
        )

        // When: Fetching extensions
        val result = dataSource.fetchAvailableExtensions()

        // Then: APK URL is resolved to absolute URL using the mock server base URL
        assertTrue(result.isSuccess)
        val extensions = result.getOrThrow()
        assertEquals(1, extensions.size)
        assertTrue(extensions[0].apkUrl?.startsWith(baseUrl) == true)
        assertTrue(extensions[0].apkUrl?.contains("apk/test-extension.apk") == true)
    }

    @Test
    fun `handles NSFW flag correctly`() = runTest {
        // Given: An NSFW extension
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        coEvery { repoRepository.getRepositories() } returns flowOf(listOf(baseUrl))

        val nsfwExtension = MinifiedExtensionDto(
            name = "NSFW Extension",
            pkg = "com.nsfw.extension",
            apk = "nsfw.apk",
            lang = "en",
            code = 1,
            version = "1.0",
            nsfw = 1, // NSFW flag set
            sources = emptyList()
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(listOf(nsfwExtension)))
        )

        // When: Fetching extensions
        val result = dataSource.fetchAvailableExtensions()

        // Then: isNsfw is set to true
        assertTrue(result.isSuccess)
        val extensions = result.getOrThrow()
        assertEquals(1, extensions.size)
        assertEquals(true, extensions[0].isNsfw)
    }
}
