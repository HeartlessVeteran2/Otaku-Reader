package app.otakureader.core.js.remote

import app.otakureader.core.extension.domain.model.InstallStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the JavaScript source index: what it parses into, and what it refuses.
 *
 * The server speaks **HTTPS**, which is not incidental to the setup — the data source rejects
 * anything else, so a plain-HTTP MockWebServer would make every test here fail for the wrong
 * reason and the refusal itself would go untested. Serving real TLS means the happy paths prove
 * the guard lets legitimate traffic through, and [a plain-http script url is refused] proves it
 * stops the rest.
 */
class JsExtensionRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: JsExtensionRemoteDataSource

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()

        dataSource = JsExtensionRemoteDataSource(
            OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .build()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl() = server.url("/").toString().trimEnd('/')

    private fun indexBody(id: String, name: String, versionCode: Int = 1) = """
        [
          {
            "id": "$id",
            "name": "$name",
            "baseUrl": "https://example.test",
            "lang": "en",
            "sourceCodeUrl": "js/$id.js",
            "version": "1.2.3",
            "versionCode": $versionCode,
            "isNsfw": false,
            "hasCloudflare": true
          }
        ]
    """.trimIndent()

    @Test
    fun `an index entry becomes a JavaScript-flagged extension`() = runTest {
        server.enqueue(MockResponse().setBody(indexBody("mangadex-en", "MangaDex")))

        val extensions = dataSource.fetchAvailable(listOf(baseUrl())).extensions

        assertEquals(1, extensions.size)
        val extension = extensions.single()
        // The discriminator is what install and uninstall route on, so it is the load-bearing
        // assertion here — a row that parsed perfectly but arrived with isJavaScript = false
        // would be sent to the APK installer.
        assertTrue(extension.isJavaScript)
        assertEquals("mangadex-en", extension.pkgName)
        assertEquals("MangaDex", extension.name)
        assertEquals(InstallStatus.AVAILABLE, extension.status)
        assertEquals("1.2.3", extension.versionName)
        assertTrue(extension.hasCloudflare)
        // A relative sourceCodeUrl resolves against the repository base — the script really does
        // live on the repository host.
        assertEquals("${baseUrl()}/js/mangadex-en.js", extension.apkUrl)
        // ...but the SOURCE's base URL is the site it scrapes, which is a different host. The
        // fixture deliberately uses a value distinct from the server so the two cannot be
        // confused: an earlier version shadowed the DTO's field with the repository URL
        // parameter and handed every source the index host. Nothing crashed — sources just
        // returned nothing, which is why only an assertion catches it.
        assertEquals("https://example.test", extension.sources.single().baseUrl)
        assertEquals(baseUrl(), extension.repoUrl)
        // There is no APK and no signature to verify; both must be absent rather than faked.
        assertNull(extension.apkPath)
        assertNull(extension.signatureHash)
        assertEquals(false, extension.isTrusted)
    }

    @Test
    fun `the index is requested from the javascript path`() = runTest {
        server.enqueue(MockResponse().setBody(indexBody("a", "A")))

        dataSource.fetchAvailable(listOf(baseUrl()))

        // A distinct path is what keeps the two backends from misreading each other's indexes.
        assertEquals("/js/index.json", server.takeRequest().path)
    }

    /**
     * Novel and anime entries are not offered.
     *
     * `JsSource` implements the manga contract, so one of these would install cleanly and then
     * fail on every read — an entry the user can add but cannot use, which is worse than one
     * that never appeared. Stage 7 adds the novel runtime and relaxes the filter in the same
     * change that makes the entries usable.
     */
    @Test
    fun `only manga entries are offered`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"id":"m","name":"Manga","baseUrl":"https://a.test","lang":"en",
                   "sourceCodeUrl":"js/m.js"},
                  {"id":"n","name":"Novel","baseUrl":"https://b.test","lang":"en",
                   "sourceCodeUrl":"js/n.js","itemType":"novel"},
                  {"id":"a","name":"Anime","baseUrl":"https://c.test","lang":"en",
                   "sourceCodeUrl":"js/a.js","itemType":"anime"}
                ]
                """.trimIndent()
            )
        )

        val extensions = dataSource.fetchAvailable(listOf(baseUrl())).extensions

        // The entry with no itemType at all must survive — most real indexes omit it.
        assertEquals(listOf("m"), extensions.map { it.pkgName })
    }

    @Test
    fun `a repository serving neither index path stays silent`() = runTest {
        // Both paths are tried, so both have to be answered. Enqueuing only one would leave the
        // second request waiting on an empty dispatcher until the client's read timeout, which
        // makes the test slow and — worse — pass for a reason unrelated to what it claims.
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))

        val result = dataSource.fetchAvailable(listOf(baseUrl()))

        assertTrue(result.extensions.isEmpty())
        // This is the *common* case, not a fault: the APK backend reads `index.min.json` first and
        // documents it as the usual third-party format, so a min-only repository answers neither
        // path this reader asks for. Reporting it would put an error in front of every user with
        // such a repository configured, about a backend they are using correctly.
        assertNull(result.firstFailure)
    }

    @Test
    fun `a broken dedicated index is reported rather than hidden by the combined one`() = runTest {
        // 500, not 404 — the distinction is the whole point. A repository publishing
        // /js/index.json has declared itself a JavaScript repository, so its index being
        // unreachable is a real fault; falling through to the combined path would let a
        // successfully-parsed APK index there bury it and report the refresh as fine.
        server.enqueue(MockResponse().setResponseCode(500))

        val result = dataSource.fetchAvailable(listOf(baseUrl()))

        assertNotNull(result.firstFailure)
        // And the combined path must never have been asked: one request, to the dedicated path.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `one unreachable repository does not suppress another`() = runTest {
        // One response each. A 500 on the dedicated path is a fault, not an absence, so it stops
        // there rather than falling through to the combined path — the first repository consumes
        // exactly one response and the second gets its own index.
        //
        // This is worth stating because the count is invisible until it is wrong: an earlier
        // version of this test enqueued two responses for the first repository, and once the
        // fallthrough narrowed to 404 only, the second repository silently received the leftover
        // 500 instead of its index. The assertion below is what caught it.
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(indexBody("survivor", "Survivor")))

        val extensions = dataSource.fetchAvailable(listOf(baseUrl(), baseUrl())).extensions

        assertEquals(listOf("survivor"), extensions.map { it.pkgName })
    }

    @Test
    fun `the higher versionCode wins when two repositories offer the same source`() = runTest {
        // The collision is the point: a dedupe test where the ids differ proves nothing about
        // the rule it claims to cover.
        server.enqueue(MockResponse().setBody(indexBody("dupe", "Old build", versionCode = 3)))
        server.enqueue(MockResponse().setBody(indexBody("dupe", "New build", versionCode = 9)))

        val extensions = dataSource.fetchAvailable(listOf(baseUrl(), baseUrl())).extensions

        assertEquals(1, extensions.size)
        assertEquals("New build", extensions.single().name)
        assertEquals(9, extensions.single().versionCode)
    }

    @Test
    fun `a plain-http script url is refused`() = runTest {
        // A script has no signature, so transport is the only control on what gets executed.
        val error = runCatching { dataSource.downloadScript("http://example.test/source.js") }
            .exceptionOrNull()

        assertTrue("expected SecurityException, got $error", error is SecurityException)
    }

    @Test
    fun `a script downloads over https`() = runTest {
        server.enqueue(MockResponse().setBody("const source = {};"))

        val script = dataSource.downloadScript("${baseUrl()}/js/source.js")

        assertEquals("const source = {};", script)
    }

    /**
     * A scheme downgrade mid-redirect must not be followed.
     *
     * Checking the scheme of the URL we *ask* for proves nothing on its own: OkHttp follows
     * redirects, and `followSslRedirects` defaults to true, so an `https://` script URL that
     * 302s to `http://` would be fetched in plaintext and anyone on the network path could
     * substitute the JavaScript about to be executed. The initial check would have passed while
     * the guarantee behind it was gone.
     *
     * Asserted against a real redirect rather than trusting the flag's documentation — the same
     * reason `RedirectHeaderPropagationTest` exists.
     */
    @Test
    fun `an https to http redirect is not followed`() = runTest {
        val plaintext = MockWebServer()
        plaintext.start()
        try {
            plaintext.enqueue(MockResponse().setBody("malicious()"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", plaintext.url("/js/source.js").toString())
            )

            val error = runCatching { dataSource.downloadScript("${baseUrl()}/js/source.js") }
                .exceptionOrNull()

            assertTrue("expected a fetch failure, got $error", error is JsExtensionFetchException)
            // The state that matters is what did NOT happen: the plaintext host was never asked.
            // Asserting only on the exception would pass even if the body had been fetched.
            assertEquals(0, plaintext.requestCount)
        } finally {
            plaintext.shutdown()
        }
    }

    @Test
    fun `an oversized script is refused rather than truncated`() = runTest {
        val oversized = "x".repeat((JsExtensionRemoteDataSource.MAX_SCRIPT_BYTES + 1024).toInt())
        server.enqueue(MockResponse().setBody(oversized))

        val error = runCatching { dataSource.downloadScript("${baseUrl()}/js/huge.js") }
            .exceptionOrNull()

        // Truncation is the failure worth preventing: a half-downloaded script installs as if
        // whole and then fails somewhere inside the engine, far from its cause.
        assertNotNull(error)
        assertTrue("expected a fetch failure, got $error", error is JsExtensionFetchException)
    }

    // -----------------------------------------------------------------------------------------
    // The real Mangayomi index shape
    //
    // Every entry below is copied from https://kodjodevf.github.io/mangayomi-extensions/index.json
    // with only the URLs pointed at the test server. The types are the point: `id` and `itemType`
    // are JSON *numbers* there, and `sourceCodeLanguage` distinguishes the Dart majority from the
    // JavaScript sources this backend can actually run.
    // -----------------------------------------------------------------------------------------

    /** One JavaScript manga source and one Dart one, exactly as the live index writes them. */
    private fun mangayomiIndexBody() = """
        [
          {
            "name": "JavaScript Source",
            "id": 652112892,
            "baseUrl": "https://js.example.test",
            "lang": "en",
            "iconUrl": "https://icons.example.test/js.png",
            "isNsfw": false,
            "hasCloudflare": false,
            "sourceCodeUrl": "js/real.js",
            "apiUrl": "https://api.example.test",
            "version": "0.0.35",
            "isManga": true,
            "itemType": 0,
            "sourceCodeLanguage": 1
          },
          {
            "name": "Dart Source",
            "id": 638504049,
            "baseUrl": "https://dart.example.test",
            "lang": "en",
            "isNsfw": false,
            "hasCloudflare": false,
            "sourceCodeUrl": "dart/manga/multisrc/madara/madara.dart",
            "apiUrl": "",
            "version": "0.1.3",
            "isManga": true,
            "itemType": 0,
            "sourceCodeLanguage": 0
          }
        ]
    """.trimIndent()

    @Test
    fun `the live Mangayomi index decodes despite numeric id and itemType`() = runTest {
        server.enqueue(MockResponse().setBody(mangayomiIndexBody()))

        val result = dataSource.fetchAvailable(listOf(baseUrl()))

        // Before this was fixed the decode threw on `id` and, had it not, the numeric `itemType`
        // could never equal "manga" and the whole index would have filtered down to nothing. Both
        // failures look identical from outside — a repository that reports no sources — so assert
        // on the surviving entry rather than merely on the absence of an exception.
        assertNull(result.firstFailure)
        val extension = result.extensions.single()
        assertEquals("652112892", extension.pkgName)
        assertEquals("JavaScript Source", extension.name)
    }

    @Test
    fun `Dart entries sharing the index are dropped`() = runTest {
        server.enqueue(MockResponse().setBody(mangayomiIndexBody()))

        val result = dataSource.fetchAvailable(listOf(baseUrl()))

        // The rule that actually bites: both entries are manga, both are `itemType` 0, and they
        // differ only by `sourceCodeLanguage`. A test with one entry would pass with the filter
        // deleted. A Dart file that survived here would download, install and register exactly
        // like a working source, and fail only when the user first browsed it.
        assertEquals(1, result.extensions.size)
        assertTrue(
            "a Dart source reached the installable list: ${result.extensions.map { it.name }}",
            result.extensions.none { it.name == "Dart Source" },
        )
    }

    @Test
    fun `apiUrl survives onto the source so API-backed extensions can build requests`() = runTest {
        server.enqueue(MockResponse().setBody(mangayomiIndexBody()))

        val source = dataSource.fetchAvailable(listOf(baseUrl())).extensions.single().sources.single()

        // Sources that set apiUrl build essentially every request from it. Dropping the field
        // produces requests against "undefined/..." that fail as ordinary HTTP errors, which
        // gives no hint that the manifest was the problem.
        assertEquals("https://api.example.test", source.apiUrl)
        assertEquals("https://js.example.test", source.baseUrl)
    }

    @Test
    fun `a version is ordered by precedence when the index publishes no versionCode`() = runTest {
        // The Mangayomi index carries no versionCode at all. Falling back to a constant would
        // leave every installed source looking permanently up to date, so it is derived from the
        // version string — and the derivation has to order by segment precedence, not by sum.
        // 1.0.0 against 0.9.9 tells those two rules apart: summing the segments gives 1 and 18, so
        // a sum ranks the older build higher, while packing gives 1_000_000 and 9_009 — correct.
        val older = JsExtensionDto(id = "a", name = "a", baseUrl = "u", lang = "en", version = "0.9.9")
        val newer = JsExtensionDto(id = "a", name = "a", baseUrl = "u", lang = "en", version = "1.0.0")

        assertTrue(
            "1.0.0 (${newer.effectiveVersionCode}) must outrank 0.9.9 (${older.effectiveVersionCode})",
            newer.effectiveVersionCode > older.effectiveVersionCode,
        )
    }

    @Test
    fun `an explicit versionCode still wins over the derived one`() = runTest {
        val explicit = JsExtensionDto(
            id = "a", name = "a", baseUrl = "u", lang = "en", version = "0.0.1", versionCode = 77,
        )

        assertEquals(77, explicit.effectiveVersionCode)
    }

    @Test
    fun `a repository serving only an APK index contributes nothing without reporting a failure`() = runTest {
        // No /js/index.json...
        server.enqueue(MockResponse().setResponseCode(404))
        // ...and an /index.json in the APK backend's shape, which this reader cannot read.
        server.enqueue(
            MockResponse().setBody(
                """[{"name":"Some APK","pkg":"eu.kanade.tachiyomi.en.some","apk":"some.apk","lang":"en","code":14}]""",
            ),
        )

        val result = dataSource.fetchAvailable(listOf(baseUrl()))

        // The state that matters is the *absence* of a reported failure. That filename is shared
        // with the APK backend, so a foreign index there is the ordinary answer from an APK-only
        // repository — surfacing it would put an error in front of every user who has Keiyoushi
        // configured and is using it correctly.
        assertEquals(emptyList<String>(), result.extensions.map { it.name })
        assertNull(result.firstFailure)
    }

    @Test
    fun `the combined index is only consulted when the dedicated one is absent`() = runTest {
        server.enqueue(MockResponse().setBody(mangayomiIndexBody()))

        dataSource.fetchAvailable(listOf(baseUrl()))

        // One request, to the dedicated path. Asserting the request count is what stops this from
        // silently becoming "always fetch both", which would double the traffic of every refresh
        // against every configured repository.
        assertEquals(1, server.requestCount)
        assertEquals(
            JsExtensionRemoteDataSource.DEDICATED_INDEX_PATH,
            server.takeRequest().path,
        )
    }
}
