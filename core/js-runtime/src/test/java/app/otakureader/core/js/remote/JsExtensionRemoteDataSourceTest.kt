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

    @Test
    fun `a repository with no javascript index contributes nothing without failing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val extensions = dataSource.fetchAvailable(listOf(baseUrl())).extensions

        // Most repositories serve only APKs. That is a normal answer, not an error — throwing
        // here would turn the common case into a user-visible failure.
        assertTrue(extensions.isEmpty())
    }

    @Test
    fun `one unreachable repository does not suppress another`() = runTest {
        // Two repos, first 404s. Both are read in order, so the enqueue order matches.
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
}
