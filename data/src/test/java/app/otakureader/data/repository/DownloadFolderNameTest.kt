package app.otakureader.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.data.download.DownloadManager
import app.otakureader.data.download.DownloadProvider
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.toSourceId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [DownloadRepositoryImpl.downloadFolderNameFor] (#1256).
 *
 * The whole point of this function is which name it picks when picking the *obvious* one would
 * lose files. Every download on every device predating #1256 is filed under the numeric source
 * key, so a resolver that returned a display name without checking disk would point every read at
 * a directory that does not exist and every downloaded chapter would silently disappear from the
 * app. These tests are therefore mostly about the fallbacks, not the happy path.
 */
@RunWith(AndroidJUnit4::class)
class DownloadFolderNameTest {

    private lateinit var context: Context
    private lateinit var sourceRepository: SourceRepository
    private lateinit var downloadManager: DownloadManager
    private lateinit var downloadPreferences: DownloadPreferences
    private var recorded = mutableMapOf<Long, String>()

    private val sourceId = "en.mangadex"
    private val key = sourceId.toSourceId()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        downloadManager = mockk(relaxed = true) {
            every { downloads } returns MutableStateFlow(emptyList<DownloadItem>())
        }
        sourceRepository = mockk(relaxed = true)
        recorded = mutableMapOf()
        downloadPreferences = mockk(relaxed = true) {
            every { sourceFolderNames } answers { flowOf(recorded.toMap()) }
            coEvery { setSourceFolderName(any(), any()) } answers {
                recorded[firstArg()] = secondArg()
            }
        }
        DownloadProvider.getRootDir(context).deleteRecursively()
    }

    private fun repository() = DownloadRepositoryImpl(
        context = context,
        downloadManager = downloadManager,
        mangaDao = mockk<MangaDao>(relaxed = true),
        chapterDao = mockk<ChapterDao>(relaxed = true),
        sourceRepository = sourceRepository,
        downloadPreferences = downloadPreferences,
        scope = TestScope(StandardTestDispatcher()).backgroundScope,
    )

    private fun source(id: String, name: String): MangaSource = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.name } returns name
    }

    private fun withSources(vararg sources: MangaSource) {
        coEvery { sourceRepository.getSourceByKey(any()) } answers {
            val wanted = firstArg<Long>()
            sources.firstOrNull { it.id.toSourceId() == wanted }
        }
        every { sourceRepository.getSources() } returns flowOf(sources.toList())
    }

    private fun makeSourceDir(name: String): File =
        File(DownloadProvider.getRootDir(context), name).apply { mkdirs() }

    /**
     * The regression this whole issue exists to prevent. Chapters are on disk under the number,
     * so that is the only answer that finds them — the readable name is not available until the
     * migration has actually moved them.
     */
    @Test
    fun keepsTheNumericKeyWhileTheChaptersAreStillFiledUnderIt() = runTest {
        withSources(source(sourceId, "MangaDex"))
        makeSourceDir(key.toString())

        assertEquals(key.toString(), repository().downloadFolderNameFor(key))
    }

    /** After the migration has renamed the folder, reads follow it. */
    @Test
    fun usesTheDisplayNameOnceTheFolderHasBeenRenamed() = runTest {
        withSources(source(sourceId, "MangaDex"))
        makeSourceDir("MangaDex")

        assertEquals("MangaDex", repository().downloadFolderNameFor(key))
    }

    /** Nothing downloaded yet, so there is nothing to lose and new files get a readable home. */
    @Test
    fun usesTheDisplayNameWhenNothingIsOnDiskForThatSourceYet() = runTest {
        withSources(source(sourceId, "MangaDex"))

        assertEquals("MangaDex", repository().downloadFolderNameFor(key))
    }

    /**
     * An uninstalled extension has no display name to resolve to, and its downloads must stay
     * readable rather than collapsing into a shared "Unknown".
     */
    @Test
    fun keepsTheNumericKeyWhenNoLoadedSourceOwnsTheKey() = runTest {
        withSources()
        makeSourceDir(key.toString())

        assertEquals(key.toString(), repository().downloadFolderNameFor(key))
    }

    /**
     * Two sources sharing a display name is realistic now that both backends ship — the APK and
     * JavaScript catalogues each carry a "MangaDex". Filing both under one folder would interleave
     * two catalogues in one tree, so both keep their numbers.
     *
     * Asserted for *both* sources, not just one: a rule that demoted only the loser would still
     * put one catalogue in the shared folder.
     */
    @Test
    fun keepsTheNumericKeyWhenTwoLoadedSourcesShareADisplayName() = runTest {
        val apk = source("en.mangadex.apk", "MangaDex")
        val js = source("en.mangadex.js", "MangaDex")
        withSources(apk, js)

        val repo = repository()
        assertEquals("en.mangadex.apk".toSourceId().toString(), repo.downloadFolderNameFor("en.mangadex.apk".toSourceId()))
        assertEquals("en.mangadex.js".toSourceId().toString(), repo.downloadFolderNameFor("en.mangadex.js".toSourceId()))
    }

    /**
     * The migration end to end, and the reason #1256 was more than a one-line change.
     *
     * `DownloadFolderMigrationWorker` and `migrateSourceFolderNames` have both existed and been
     * enqueued for a long time, and renamed nothing: they resolved names through the old
     * `sourceId.toString()`, so the map they built was the identity and every rename was skipped
     * as a no-op. This asserts the rename actually happens now *and* that a read follows it —
     * which together are the property that keeps downloaded chapters reachable.
     */
    @Test
    fun migrationRenamesTheNumericFolderAndReadsThenFollowIt() = runTest {
        withSources(source(sourceId, "MangaDex"))
        val numericDir = makeSourceDir(key.toString())
        val chapter = File(numericDir, "Some Manga/Chapter 1").apply { mkdirs() }
        val page = File(chapter, "0.jpg").apply { writeText("page") }
        val repo = repository()
        // Prime the cache with the pre-migration answer, so this also covers the invalidation.
        assertEquals(key.toString(), repo.downloadFolderNameFor(key))

        assertEquals(1, repo.migrateSourceFolderNames())

        assertEquals("MangaDex", repo.downloadFolderNameFor(key))
        val moved = File(DownloadProvider.getRootDir(context), "MangaDex/Some Manga/Chapter 1/0.jpg")
        assertTrue("the downloaded page must move with the folder, not be left behind", moved.isFile)
        assertFalse("the numeric folder must be gone, not duplicated", numericDir.exists())
        assertEquals("page", moved.readText())
        assertFalse(page.exists())
    }

    /**
     * A source whose folder cannot be given a readable name is left exactly where it is. Renaming
     * it has no name to rename to, and it has to stay readable.
     */
    @Test
    fun migrationLeavesFoldersWhoseSourceCannotBeResolved() = runTest {
        withSources()
        makeSourceDir(key.toString())

        assertEquals(0, repository().migrateSourceFolderNames())
        assertTrue(File(DownloadProvider.getRootDir(context), key.toString()).isDirectory)
    }

    /**
     * The name is sanitized here, not by the caller, because [DownloadProvider] sanitizes again on
     * every path build and the migration renames to the sanitized form. If this returned the raw
     * name the two would still agree — but the collision check above would not, since it compares
     * sanitized names.
     */
    @Test
    fun sanitizesTheDisplayNameSoItMatchesWhatTheMigrationRenamedTo() = runTest {
        val slashed = source("en.weird", "Manga/Dex")
        withSources(slashed)
        makeSourceDir("Manga_Dex")

        assertEquals("Manga_Dex", repository().downloadFolderNameFor("en.weird".toSourceId()))
    }

    /**
     * The case that made a recorded mapping necessary rather than nice to have. Once the extension
     * is gone there is no display name left to re-derive, so a resolver that only computes would
     * answer with the number and every already-migrated download would vanish from the app.
     */
    @Test
    fun findsMigratedDownloadsAfterTheExtensionIsUninstalled() = runTest {
        withSources(source(sourceId, "MangaDex"))
        makeSourceDir(key.toString())
        val repo = repository()
        repo.migrateSourceFolderNames()

        // The extension goes away; nothing on disk changes.
        withSources()

        assertEquals("MangaDex", repo.downloadFolderNameFor(key))
    }

    /**
     * Both directories present: the display one was claimed by something else (the migration skips
     * a target that already exists rather than merging into it), so this source's chapters are
     * still under the number. Answering with the display name would show another source's library
     * and hide this one's.
     */
    @Test
    fun prefersTheNumericFolderWhenBothExist() = runTest {
        withSources(source(sourceId, "MangaDex"))
        makeSourceDir(key.toString())
        makeSourceDir("MangaDex")

        assertEquals(key.toString(), repository().downloadFolderNameFor(key))
    }

    /**
     * The migration refuses to rename onto an existing directory, so that directory is somebody
     * else's. Recording it against this source would point every read at the wrong library — and
     * because a recorded folder outranks the numeric one, it would defeat the numeric-first rule
     * that exists precisely for this case.
     */
    @Test
    fun doesNotRecordAFolderTheMigrationCouldNotClaim() = runTest {
        withSources(source(sourceId, "MangaDex"))
        makeSourceDir(key.toString())
        // Somebody else already holds the target name.
        makeSourceDir("MangaDex")
        val repo = repository()

        assertEquals("nothing may be renamed onto an occupied name", 0, repo.migrateSourceFolderNames())

        assertTrue("no mapping may be recorded for a rename that did not happen", recorded.isEmpty())
        assertEquals(key.toString(), repo.downloadFolderNameFor(key))
    }

    /**
     * A display name is not unique over time. Source A migrates to `MangaDex/` and is uninstalled;
     * a different source later arrives under the same name with no folder of its own. Without an
     * ownership check it would adopt A's downloads and start writing its own in among them.
     * `displayNameFor`'s collision guard cannot catch this — it only compares loaded sources, and
     * A is gone.
     */
    @Test
    fun doesNotAdoptAFolderRecordedToAnotherSource() = runTest {
        withSources(source(sourceId, "MangaDex"))
        makeSourceDir(key.toString())
        val repo = repository()
        repo.migrateSourceFolderNames()
        assertEquals("MangaDex", repo.downloadFolderNameFor(key))

        // A different source, same display name, nothing of its own on disk.
        val newcomer = "en.mangadex.other"
        withSources(source(newcomer, "MangaDex"))

        assertEquals(newcomer.toSourceId().toString(), repository().downloadFolderNameFor(newcomer.toSourceId()))
    }
}
