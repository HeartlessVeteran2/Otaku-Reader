package app.otakureader.core.extension.data

import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSourceImpl
import app.otakureader.core.extension.domain.backend.JsExtensionBackend
import app.otakureader.core.extension.domain.backend.JsExtensionFetch
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Covers failure isolation between the two source backends.
 *
 * This exists because the inverse shipped in Stage 4a: the isolation ran one direction only, so a
 * single thrown exception on the APK path silently dropped every JavaScript source. The comment
 * at the time said the two backends shared nothing, and it read convincingly enough that nobody
 * re-derived it. Both directions are asserted here so neither can regress unnoticed.
 */
class JsBackendIsolationTest {

    private val unreachableRepo = "https://unreachable.invalid/repo"

    private fun repoRepository(): ExtensionRepoRepository = mockk(relaxed = true) {
        coEvery { getRepositories() } returns flowOf(listOf(unreachableRepo))
    }

    /**
     * A client that fails every call without touching the network.
     *
     * The APK fetch has to fail for these tests to mean anything, but it must fail *locally*.
     * Relying on `unreachable.invalid` not resolving would make the result depend on the
     * sandbox's DNS — a test that passes for a reason unrelated to what it claims to check, and
     * that turns into a slow or flaky one the moment a resolver answers wildcard queries.
     */
    private fun failingClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { throw IOException("offline in tests") }
        .build()

    private fun jsExtension(pkgName: String) = Extension(
        id = 1L,
        pkgName = pkgName,
        name = pkgName,
        versionCode = 1,
        versionName = "1.0.0",
        sources = emptyList(),
        status = InstallStatus.AVAILABLE,
        apkPath = null,
        apkUrl = "https://example.test/$pkgName.js",
        iconUrl = null,
        lang = "en",
        isNsfw = false,
        installDate = null,
        signatureHash = null,
        isJavaScript = true,
    )

    /**
     * The Stage 4a defect, as a test.
     *
     * The APK repository here is unreachable, so every APK fetch fails. Before the fix that made
     * this pass, the early `return Result.failure(...)` ran before the JavaScript backend was
     * ever consulted — the user saw "all repositories failed" and no sources at all, even though
     * the JavaScript ones were fetchable the whole time.
     */
    @Test
    fun `a total APK failure still surfaces JavaScript sources`() = runTest {
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { fetchAvailable(any()) } returns
                JsExtensionFetch(listOf(jsExtension("js-source")), servedAnyIndex = true)
        }

        val result = ExtensionRemoteDataSourceImpl(
            repoRepository = repoRepository(),
            httpClient = failingClient(),
            jsBackend = jsBackend,
        ).fetchAvailableExtensions()

        assertTrue("expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(listOf("js-source"), result.getOrThrow().map { it.pkgName })
    }

    /**
     * The same isolation in the other direction.
     *
     * A JavaScript backend that throws must not take the APK path down with it. It cannot be
     * asserted by checking the returned APK list here — the repository is unreachable in this
     * fixture — so the observable claim is the narrower one that actually distinguishes the two
     * behaviours: the call completes and reports the ordinary all-repositories-failed error,
     * rather than propagating the JavaScript backend's exception.
     */
    @Test
    fun `a throwing JavaScript backend does not propagate its exception`() = runTest {
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { fetchAvailable(any()) } throws IllegalStateException("js index exploded")
        }

        val result = ExtensionRemoteDataSourceImpl(
            repoRepository = repoRepository(),
            httpClient = failingClient(),
            jsBackend = jsBackend,
        ).fetchAvailableExtensions()

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "expected the repository failure, not the JS one: $message",
            message.contains("extension repositories failed"),
        )
    }

    /**
     * A JavaScript-only repository whose index is valid but empty is not a failure.
     *
     * Its APK endpoints legitimately 404 and its JavaScript list is legitimately empty, so a
     * check that read emptiness as failure would tell a user whose setup works perfectly that
     * every repository failed. The condition asks whether an index was *served*, which is a
     * different fact from whether it produced anything.
     */
    @Test
    fun `an empty but valid JavaScript index is not a total failure`() = runTest {
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { fetchAvailable(any()) } returns
                JsExtensionFetch(emptyList(), servedAnyIndex = true)
        }

        val result = ExtensionRemoteDataSourceImpl(
            repoRepository = repoRepository(),
            httpClient = failingClient(),
            jsBackend = jsBackend,
        ).fetchAvailableExtensions()

        assertTrue("expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    /**
     * The counterpart: nothing served an index at all, so the failure is real and reported.
     * Without this case the test above would pass just as happily if the check were removed.
     */
    @Test
    fun `no index served anywhere is still a total failure`() = runTest {
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { fetchAvailable(any()) } returns
                JsExtensionFetch(emptyList(), servedAnyIndex = false)
        }

        val result = ExtensionRemoteDataSourceImpl(
            repoRepository = repoRepository(),
            httpClient = failingClient(),
            jsBackend = jsBackend,
        ).fetchAvailableExtensions()

        assertTrue(result.isFailure)
    }

    /**
     * With no JavaScript backend wired in, behaviour is exactly what it was before this change.
     * That is what lets the existing APK tests keep standing as the regression net for that path.
     */
    @Test
    fun `without a JavaScript backend a total failure is still a failure`() = runTest {
        val result = ExtensionRemoteDataSourceImpl(
            repoRepository = repoRepository(),
            httpClient = failingClient(),
        ).fetchAvailableExtensions()

        assertTrue(result.isFailure)
    }
}
