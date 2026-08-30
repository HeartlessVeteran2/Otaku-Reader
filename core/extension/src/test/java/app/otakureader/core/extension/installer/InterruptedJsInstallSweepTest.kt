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
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The orphan window no in-process guard can close (#1229, item 2a).
 *
 * Installing a JavaScript source writes to two stores: the script under `filesDir/js-exts/`, and
 * the `extensions` row. Uninstall finds its target *through the database*, so a script with no row
 * is a source that still executes and can never be removed — and it holds whatever that source
 * saved, including logins.
 *
 * Every in-process failure between those writes is already reconciled. Process death is not: if the
 * app is killed after the script is registered and before the row is written, no `finally` runs.
 * A marker written to disk before the install survives that, and the sweep at next launch acts on
 * whatever is still marked.
 *
 * These tests drive the sweep directly with a pre-seeded marker, which is the only way to reproduce
 * "the previous process died here" — the point is precisely that no code ran to clean up.
 */
class InterruptedJsInstallSweepTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private val loader = mockk<ExtensionLoader>(relaxed = true)
    private val remoteDataSource = mockk<ExtensionRemoteDataSource>(relaxed = true)

    private val sourceId = "en.mangadex/v1"

    @Before
    fun setUp() {
        context = mockk(relaxed = true) {
            coEvery { filesDir } returns temporaryFolder.root
        }
    }

    /** Simulates a process that died mid-install: the marker is on disk, nothing else ran. */
    private fun seedInterruptedInstall(id: String = sourceId) {
        PendingJsInstalls(context).begin(id)
    }

    private fun installer(
        repository: ExtensionRepository,
        jsBackend: JsExtensionBackend,
    ) = ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)

    private fun installedJsRow() = Extension(
        id = 1L,
        pkgName = sourceId,
        name = "MangaDex",
        versionCode = 1,
        versionName = "1.0.0",
        sources = emptyList(),
        status = InstallStatus.INSTALLED,
        apkPath = null,
        apkUrl = "https://example.test/x.js",
        iconUrl = null,
        lang = "en",
        isNsfw = false,
        installDate = null,
        signatureHash = null,
        isJavaScript = true,
    )

    /**
     * No row means nothing can reach the script through the normal uninstall path, so it goes.
     * This is the orphan the whole mechanism exists for.
     */
    @Test
    fun `an install with no row leaves the script removed`() = runTest {
        seedInterruptedInstall()
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { getExtension(sourceId) } returns null
        }
        val jsBackend = mockk<JsExtensionBackend>(relaxed = true) {
            coEvery { uninstall(sourceId) } returns Result.success(Unit)
        }

        val removed = installer(repository, jsBackend).sweepInterruptedJsInstalls()

        assertEquals(1, removed)
        coVerify(exactly = 1) { jsBackend.uninstall(sourceId) }
        assertEquals("the marker must be cleared", emptyList<String>(), PendingJsInstalls(context).pending())
    }

    /**
     * The install actually completed — the process died after the row landed. Deleting here would
     * remove a working source, which is the failure mode that matters most: the sweep must be safe
     * to run on every launch.
     */
    @Test
    fun `a completed install is left alone`() = runTest {
        seedInterruptedInstall()
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { getExtension(sourceId) } returns installedJsRow()
        }
        val jsBackend = mockk<JsExtensionBackend>(relaxed = true)

        val removed = installer(repository, jsBackend).sweepInterruptedJsInstalls()

        assertEquals(0, removed)
        coVerify(exactly = 0) { jsBackend.uninstall(any()) }
        assertEquals(emptyList<String>(), PendingJsInstalls(context).pending())
    }

    /**
     * An APK row that happens to share the package name is not protection for a script — uninstall
     * would route down the APK path and leave the script behind. Same rule `reconcileFailedInstall`
     * already applies; `pkgName` is a namespace shared by both backends.
     */
    @Test
    fun `an APK row with the same name does not protect the script`() = runTest {
        seedInterruptedInstall()
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { getExtension(sourceId) } returns installedJsRow().copy(isJavaScript = false)
        }
        val jsBackend = mockk<JsExtensionBackend>(relaxed = true) {
            coEvery { uninstall(sourceId) } returns Result.success(Unit)
        }

        val removed = installer(repository, jsBackend).sweepInterruptedJsInstalls()

        assertEquals(1, removed)
        coVerify(exactly = 1) { jsBackend.uninstall(sourceId) }
    }

    /**
     * A row in a non-installed state is not evidence the install finished. `ERROR` in particular is
     * transient — the next refresh replaces it with `AVAILABLE` — so treating any row as proof
     * would orphan the script one refresh later, which is a mistake an earlier version of the
     * in-process reconciler actually made.
     */
    @Test
    fun `a row that is not INSTALLED does not count as completed`() = runTest {
        seedInterruptedInstall()
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { getExtension(sourceId) } returns installedJsRow().copy(status = InstallStatus.ERROR)
        }
        val jsBackend = mockk<JsExtensionBackend>(relaxed = true) {
            coEvery { uninstall(sourceId) } returns Result.success(Unit)
        }

        val removed = installer(repository, jsBackend).sweepInterruptedJsInstalls()

        assertEquals(1, removed)
    }

    /**
     * A failed lookup deletes nothing and keeps the marker, so the sweep simply runs again next
     * launch. This is the reverse of the fail-closed rule at install time and for the same reason:
     * there, refusing avoids creating an unreachable artifact; here, refusing avoids destroying a
     * reachable one on a guess.
     */
    @Test
    fun `a failed lookup destroys nothing and keeps the marker`() = runTest {
        seedInterruptedInstall()
        val repository = mockk<ExtensionRepository>(relaxed = true) {
            coEvery { getExtension(sourceId) } throws IllegalStateException("database unavailable")
        }
        val jsBackend = mockk<JsExtensionBackend>(relaxed = true)

        val removed = installer(repository, jsBackend).sweepInterruptedJsInstalls()

        assertEquals(0, removed)
        coVerify(exactly = 0) { jsBackend.uninstall(any()) }
        assertEquals(
            "the marker must survive so the next launch can retry",
            listOf(sourceId),
            PendingJsInstalls(context).pending(),
        )
    }

    /** Nothing marked, nothing to do — the ordinary case on almost every launch. */
    @Test
    fun `no markers means no work`() = runTest {
        val repository = mockk<ExtensionRepository>(relaxed = true)
        val jsBackend = mockk<JsExtensionBackend>(relaxed = true)

        val removed = installer(repository, jsBackend).sweepInterruptedJsInstalls()

        assertEquals(0, removed)
        coVerify(exactly = 0) { jsBackend.uninstall(any()) }
    }

    /**
     * A source id is not a safe filename — Mangayomi ids contain `/`. The marker is named by a hash
     * of the id and holds the id verbatim, so the id round-trips without a decode step whose
     * failure would delete the wrong source.
     */
    @Test
    fun `a source id containing a path separator round-trips`() = runTest {
        val awkward = "en/mangadex/v1?x=1"
        seedInterruptedInstall(awkward)

        assertEquals(listOf(awkward), PendingJsInstalls(context).pending())
    }

    /** With no JavaScript backend wired in there is nothing to sweep, and no crash. */
    @Test
    fun `no JavaScript backend is a no-op`() = runTest {
        seedInterruptedInstall()
        val repository = mockk<ExtensionRepository>(relaxed = true)

        val installer = ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend = null)

        assertEquals(0, installer.sweepInterruptedJsInstalls())
    }

    /** Guards the marker directory location, since the sweep only sees what `pending()` lists. */
    @Test
    fun `markers live under filesDir`() {
        seedInterruptedInstall()
        val dir = File(temporaryFolder.root, "js-installs-pending")
        assertEquals(1, dir.listFiles()?.size)
    }
}
