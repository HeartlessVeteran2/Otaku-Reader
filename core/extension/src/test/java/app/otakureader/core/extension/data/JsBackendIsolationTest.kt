package app.otakureader.core.extension.data

import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSourceImpl
import app.otakureader.core.extension.domain.backend.JsExtensionBackend
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
            coEvery { fetchAvailable(any()) } returns listOf(jsExtension("js-source"))
        }

        val result = ExtensionRemoteDataSourceImpl(
            repoRepository = repoRepository(),
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
     * With no JavaScript backend wired in, behaviour is exactly what it was before this change.
     * That is what lets the existing APK tests keep standing as the regression net for that path.
     */
    @Test
    fun `without a JavaScript backend a total failure is still a failure`() = runTest {
        val result = ExtensionRemoteDataSourceImpl(repoRepository = repoRepository())
            .fetchAvailableExtensions()

        assertTrue(result.isFailure)
    }
}
