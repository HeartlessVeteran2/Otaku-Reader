package app.otakureader.core.extension.installer

import android.content.Context
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSource
import app.otakureader.core.extension.domain.backend.JsExtensionBackend
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.loader.ExtensionLoader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers which backend an install or uninstall is routed to.
 *
 * Routing is decided by the stored [Extension.isJavaScript] flag, never inferred from a name or a
 * URL suffix. The tests below construct the cases where a guess would go wrong, because a
 * routing test whose two extensions are obviously different proves nothing about the rule.
 */
class JsInstallRoutingTest {

    private val context = mockk<Context>(relaxed = true) {
        coEvery { filesDir } returns File(System.getProperty("java.io.tmpdir"), "js-routing-test")
    }
    private val loader = mockk<ExtensionLoader>(relaxed = true)
    private val remoteDataSource = mockk<ExtensionRemoteDataSource>(relaxed = true)

    private fun extension(pkgName: String, isJavaScript: Boolean) = Extension(
        id = 1L,
        pkgName = pkgName,
        name = pkgName,
        versionCode = 1,
        versionName = "1.0.0",
        sources = emptyList(),
        status = InstallStatus.AVAILABLE,
        apkPath = null,
        apkUrl = "https://example.test/$pkgName",
        iconUrl = null,
        lang = "en",
        isNsfw = false,
        installDate = null,
        signatureHash = null,
        isJavaScript = isJavaScript,
    )

    @Test
    fun `installing a JavaScript extension goes to the JavaScript backend`() = runTest {
        val subject = extension("mangadex-en", isJavaScript = true)
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { install(subject) } returns Result.success(Unit)
        }
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { installExtension(any<Extension>(), any()) } returns Result.success(subject)
        }

        val result = ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)
            .downloadAndInstall(subject)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { jsBackend.install(subject) }
        // The row is written only after the script is on disk. A row written first would show a
        // source in the library that fails on every call.
        coVerify(exactly = 1) { repository.installExtension(subject, "") }
    }

    /**
     * A failed script download must leave nothing claiming to be installed.
     *
     * This is the ordering the comment on `installJavaScript` asserts, checked as behaviour: the
     * database write is downstream of the backend call, so a backend failure means no row.
     */
    @Test
    fun `a failed JavaScript install writes no row`() = runTest {
        val subject = extension("broken", isJavaScript = true)
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { install(subject) } returns Result.failure(SecurityException("not https"))
        }
        val repository = mockk<ExtensionRepository>(relaxed = true)

        val result = ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)
            .downloadAndInstall(subject)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.installExtension(any<Extension>(), any()) }
    }

    /**
     * A row that could not be written must not leave the script behind.
     *
     * Uninstall finds its target through the database, so a script that is on disk and
     * registered with the engine while no row describes it can never be reached again — an
     * orphan that still executes. The backend install is rolled back to prevent that.
     */
    @Test
    fun `a failed row write rolls the script back out`() = runTest {
        val subject = extension("orphan-risk", isJavaScript = true)
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { install(subject) } returns Result.success(Unit)
            coEvery { uninstall("orphan-risk") } returns Result.success(Unit)
        }
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { installExtension(any<Extension>(), any()) } returns
                Result.failure(java.io.IOException("database is locked"))
            // No row survives the failure, so nothing can reach the script.
            coEvery { getExtension("orphan-risk") } returns null
        }

        val result = ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)
            .downloadAndInstall(subject)

        assertTrue(result.isFailure)
        // The state left behind is the whole point — asserting only the return value would pass
        // just as happily with the script still installed.
        coVerify(exactly = 1) { jsBackend.uninstall("orphan-risk") }
        // The caller is owed the error that actually caused the failure, not one from cleanup.
        assertTrue(result.exceptionOrNull() is java.io.IOException)
    }

    /**
     * A failed *update* must not destroy the source it was updating.
     *
     * `backend.install` has already replaced the script of something the user had working. An
     * unconditional rollback would uninstall it outright — deleting the source and the stored
     * preferences that routinely hold their login for that site. The row survives the failure
     * here, so the script is still reachable by uninstall and must be left alone.
     *
     * This is the case the first version of the rollback got wrong: it fixed the orphan and
     * introduced a worse failure one branch over.
     */
    @Test
    fun `a failed update does not uninstall the source it was updating`() = runTest {
        val subject = extension("already-installed", isJavaScript = true)
        val jsBackend = mockk<JsExtensionBackend>(relaxed = true) {
            coEvery { install(subject) } returns Result.success(Unit)
        }
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { installExtension(any<Extension>(), any()) } returns
                Result.failure(java.io.IOException("database is locked"))
            // The pre-existing row survives, so uninstall can still find the script.
            coEvery { getExtension("already-installed") } returns
                subject.copy(status = InstallStatus.INSTALLED)
        }

        val result = ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)
            .downloadAndInstall(subject)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { jsBackend.uninstall(any()) }
        // The row must not be left at ERROR. ERROR is transient — the next refresh does not
        // count those rows as installed and replaces them with AVAILABLE ones, which would put
        // the script live in Browse while the extension screen said it was not installed, with
        // no uninstall button anywhere. Restoring INSTALLED makes the row describe reality:
        // something really is installed, because the new script is on disk and registered.
        coVerify(exactly = 1) {
            repository.setExtensionStatus("already-installed", InstallStatus.INSTALLED)
        }
    }

    /**
     * The bug this guard exists for.
     *
     * Without the routing branch a JavaScript source falls through to the private-APK uninstall
     * path, which deletes the database row and nothing else — leaving the script on disk, still
     * registered with the engine, and its stored preferences intact. Those preferences routinely
     * hold the user's login for the site, so "uninstalled" would silently keep the credentials.
     */
    @Test
    fun `uninstalling a JavaScript extension erases it through the JavaScript backend`() = runTest {
        val subject = extension("mangadex-en", isJavaScript = true)
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { uninstall("mangadex-en") } returns Result.success(Unit)
        }
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { getExtension("mangadex-en") } returns subject
            coEvery { uninstallExtension("mangadex-en") } returns Result.success(Unit)
        }

        val result = ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)
            .uninstall("mangadex-en")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { jsBackend.uninstall("mangadex-en") }
        coVerify(exactly = 1) { repository.uninstallExtension("mangadex-en") }
    }

    /**
     * A failed erase must not drop the row.
     *
     * Dropping it would remove the source from the list the user would retry from while its
     * script and stored login were still on disk — a state no retry can reach.
     */
    @Test
    fun `a failed JavaScript uninstall keeps the row and restores its status`() = runTest {
        val subject = extension("stubborn", isJavaScript = true)
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { uninstall("stubborn") } returns Result.failure(java.io.IOException("disk full"))
        }
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { getExtension("stubborn") } returns subject
        }

        val result = ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)
            .uninstall("stubborn")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.uninstallExtension(any()) }
        // Left as UNINSTALLING the source would look stuck mid-removal while still working fine.
        coVerify { repository.setExtensionStatus("stubborn", InstallStatus.INSTALLED) }
    }

    /**
     * A failed row delete must not be announced as a removal, and must not strand the row.
     *
     * By this point the script and its stored preferences are already gone, so the row is the
     * last thing describing something that no longer exists. Leaving it at UNINSTALLING freezes
     * a dead source the user cannot act on; broadcasting a removal that did not happen makes
     * listeners drop state for an extension the database still lists.
     */
    @Test
    fun `a failed row delete is not announced and lands in ERROR`() = runTest {
        val subject = extension("half-removed", isJavaScript = true)
        val jsBackend = mockk<JsExtensionBackend> {
            coEvery { uninstall("half-removed") } returns Result.success(Unit)
        }
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { getExtension("half-removed") } returns subject
            coEvery { uninstallExtension("half-removed") } returns
                Result.failure(java.io.IOException("database is locked"))
        }

        val result = ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)
            .uninstall("half-removed")

        assertTrue(result.isFailure)
        // ERROR rather than INSTALLED: the extension really is gone, so a row claiming it is
        // installed would show a source that cannot function. ERROR also lets the next refresh
        // re-offer it as available.
        coVerify { repository.setExtensionStatus("half-removed", InstallStatus.ERROR) }
        coVerify(exactly = 0) { repository.setExtensionStatus("half-removed", InstallStatus.INSTALLED) }
    }

    /**
     * An APK extension must never reach the JavaScript backend, even with one wired in.
     */
    @Test
    fun `an APK extension is not routed to the JavaScript backend`() = runTest {
        val subject = extension("eu.kanade.tachiyomi.extension.en.mangadex", isJavaScript = false)
        val jsBackend = mockk<JsExtensionBackend>(relaxed = true)
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { getExtension(any()) } returns subject
        }

        ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)
            .downloadAndInstall(subject)

        coVerify(exactly = 0) { jsBackend.install(any()) }
    }
}
