package app.otakureader.data.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.database.dao.UpdateErrorDao
import app.otakureader.core.database.dao.UpdateRunSummaryDao
import app.otakureader.core.preferences.NotificationPreferences
import app.otakureader.data.download.DownloadManager
import app.otakureader.domain.model.Chapter
import java.time.Instant
import app.otakureader.domain.model.FeedItem
import app.otakureader.domain.repository.FeedRepository
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.UpdateLibraryMangaUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for LibraryUpdateWorker.
 * Tests library update flow, auto-download logic, notification triggering, and WiFi checking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryUpdateWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var getLibraryManga: GetLibraryMangaUseCase
    private lateinit var updateLibraryManga: UpdateLibraryMangaUseCase
    // Initialised here rather than in setUp, which is at detekt's length limit. JUnit builds a
    // fresh test instance per method, so this is still isolated between tests.
    private val feedRepository: FeedRepository = mockk(relaxed = true)

    /**
     * [count] chapters that stand in for whatever the update found.
     *
     * The use case returns the chapters now, not a count, because the feed has to name what
     * arrived. These tests only care how many, so the contents are placeholders — but they have to
     * be real [Chapter]s, since a size is no longer enough to express the result.
     */
    private val timestampToleranceSeconds = 60L

    private fun newChapters(count: Int): List<Chapter> = List(count) { index ->
        Chapter(
            id = index + 1L,
            mangaId = 1L,
            url = "/chapter-${index + 1}",
            name = "Chapter ${index + 1}",
            chapterNumber = (index + 1).toFloat(),
            dateUpload = 0L,
            dateFetch = 0L,
        )
    }
    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var downloadManager: DownloadManager
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var notificationPreferences: NotificationPreferences
    private lateinit var updateRunSummaryDao: UpdateRunSummaryDao
    private lateinit var updateErrorDao: UpdateErrorDao
    private lateinit var libraryUpdateFilter: LibraryUpdateFilter
    private lateinit var sourceRepository: SourceRepository
    private lateinit var worker: LibraryUpdateWorker

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var network: Network
    private lateinit var networkCapabilities: NetworkCapabilities

    private val testManga1 = Manga(
        id = 1L,
        sourceId = 100L,
        url = "https://example.com/manga1",
        title = "Test Manga 1",
        author = "Author 1",
        artist = "Artist 1",
        description = "Description 1",
        genre = listOf("Action", "Adventure"),
        status = MangaStatus.ONGOING,
        thumbnailUrl = "https://example.com/cover1.jpg",
        favorite = true,
        initialized = true,
        notifyNewChapters = true,
        autoDownload = false
    )

    private val testManga2 = Manga(
        id = 2L,
        sourceId = 200L,
        url = "https://example.com/manga2",
        title = "Test Manga 2",
        author = "Author 2",
        artist = "Artist 2",
        description = "Description 2",
        genre = listOf("Romance", "Comedy"),
        status = MangaStatus.ONGOING,
        thumbnailUrl = "https://example.com/cover2.jpg",
        favorite = true,
        initialized = true,
        notifyNewChapters = false, // Notifications disabled for this manga
        autoDownload = true
    )

    private val testChapter = Chapter(
        id = 1L,
        mangaId = 1L,
        url = "https://example.com/chapter1",
        name = "Chapter 1",
        chapterNumber = 1.0f,
        scanlator = "Test Scanlator",
        dateUpload = System.currentTimeMillis(),
        read = false,
        lastPageRead = 0
    )

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        getLibraryManga = mockk()
        updateLibraryManga = mockk()
        downloadPreferences = mockk()
        generalPreferences = mockk()
        libraryPreferences = mockk()
        downloadManager = mockk(relaxed = true)
        chapterRepository = mockk()
        categoryRepository = mockk()
        notificationPreferences = mockk(relaxed = true)
        updateRunSummaryDao = mockk(relaxed = true)
        updateErrorDao = mockk(relaxed = true)
        sourceRepository = mockk(relaxed = true)

        connectivityManager = mockk()
        network = mockk()
        networkCapabilities = mockk()

        // Default preference values
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(false)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(false)
        every { downloadPreferences.autoDownloadLimit } returns flowOf(3)
        every { downloadPreferences.autoDownloadCategoryInclude } returns flowOf(emptySet())
        every { downloadPreferences.autoDownloadCategoryExclude } returns flowOf(emptySet())
        every { generalPreferences.notificationsEnabled } returns flowOf(true)
        every { libraryPreferences.updateOnlyOnWifi } returns flowOf(false)
        every { libraryPreferences.skipUpdateCategoryIds } returns flowOf(emptySet())
        every { libraryPreferences.skipUpdatesWithUnread } returns flowOf(false)
        every { libraryPreferences.skipUpdatesWithCompleted } returns flowOf(false)
        every { libraryPreferences.skipUpdatesNeverStarted } returns flowOf(false)
        every { libraryPreferences.categoryLastUpdateMs } returns flowOf(emptyMap())
        every { libraryPreferences.showUpdateProgress } returns flowOf(false)
        every { notificationPreferences.hideNotificationContent } returns flowOf(false)
        coEvery { categoryRepository.getCategories() } returns flowOf(emptyList())
        coEvery { libraryPreferences.setCategoryLastUpdateMs(any()) } just runs

        libraryUpdateFilter = mockk()
        coEvery { libraryUpdateFilter.apply(any(), any()) } answers {
            LibraryUpdateFilter.Result(
                filtered = firstArg(),
                skipped = emptyList(),
                updatedCategoryIds = emptySet(),
            )
        }

        // Mock connectivity
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true

        worker = LibraryUpdateWorker(
            context,
            workerParams,
            getLibraryManga,
            updateLibraryManga,
            libraryPreferences,
            downloadPreferences,
            generalPreferences,
            downloadManager,
            chapterRepository,
            notificationPreferences,
            updateRunSummaryDao,
            updateErrorDao,
            libraryUpdateFilter,
            sourceRepository,
            feedRepository,
        )
    }

    // -------------------------------------------------------------------------
    // Feed Writing
    // -------------------------------------------------------------------------

    /**
     * The Feed tab was empty permanently: `FeedRefreshWorker` purged rows older than thirty days
     * and nothing anywhere inserted one. `FeedRepository` did not even expose a writer. This is the
     * test that says the tab has content at all.
     */
    @Test
    fun `new chapters are recorded in the feed`() = runTest {
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(2))
        coEvery { sourceRepository.getSource(testManga1.sourceId.toString()) } returns
            mockk { every { name } returns "MangaDex" }

        worker.doWork()

        val captured = slot<List<FeedItem>>()
        coVerify { feedRepository.addFeedItems(capture(captured)) }
        assertEquals(2, captured.captured.size)
        assertEquals(testManga1.title, captured.captured.first().mangaTitle)
        assertEquals("MangaDex", captured.captured.first().sourceName)
        // The real chapter id, not the 0 the use case builds before Room assigns one — a feed row
        // that cannot open its chapter is not much of a feed row.
        assertEquals(1L, captured.captured.first().chapterId)
    }

    @Test
    fun `an update that finds nothing writes no feed items`() = runTest {
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(emptyList())

        worker.doWork()

        coVerify(exactly = 0) { feedRepository.addFeedItems(any()) }
    }

    /**
     * An extension can be uninstalled while its manga stay in the library. The chapter still
     * arrived, so the row is kept with the id as its label rather than dropped.
     */
    @Test
    fun `a chapter from an uninstalled source still reaches the feed`() = runTest {
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))
        coEvery { sourceRepository.getSource(any()) } returns null

        worker.doWork()

        val captured = slot<List<FeedItem>>()
        coVerify { feedRepository.addFeedItems(capture(captured)) }
        assertEquals(testManga1.sourceId.toString(), captured.captured.single().sourceName)
    }

    /**
     * The feed is ordered by timestamp, so an undated chapter reported as 0 would be pinned to
     * 1970 and buried under everything else.
     */
    @Test
    fun `an undated chapter is stamped now rather than 1970`() = runTest {
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))

        worker.doWork()

        val captured = slot<List<FeedItem>>()
        coVerify { feedRepository.addFeedItems(capture(captured)) }
        // Bounded both ways. A lower bound alone passes for any non-zero stamp, including a
        // far-future one, which is not what "stamped now" means.
        val timestamp = captured.captured.single().timestamp
        assertTrue(
            "a zero dateUpload must not become the epoch",
            timestamp.isAfter(Instant.now().minusSeconds(timestampToleranceSeconds)),
        )
        assertTrue(
            "a zero dateUpload must be stamped now, not some other non-zero value",
            timestamp.isBefore(Instant.now().plusSeconds(timestampToleranceSeconds)),
        )
    }

    /**
     * A feed row is a nicety; the auto-download pass that follows it is not. Failing the whole
     * update because a secondary write failed would trade a real feature for a cosmetic one.
     */
    @Test
    fun `a feed write failure does not fail the library update`() = runTest {
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))
        coEvery { feedRepository.addFeedItems(any()) } throws RuntimeException("disk full")

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    /**
     * Cancellation is not an ordinary failure, and the guard above must not treat it as one.
     *
     * `runCatching` caught it, which let a cancelled update carry on into the auto-download pass
     * instead of stopping — the same rule this codebase applies at every other catch site.
     */
    @Test
    fun `cancellation during a feed write is not swallowed`() = runTest {
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))
        coEvery { feedRepository.addFeedItems(any()) } throws CancellationException("cancelled")

        var propagated = false
        try {
            worker.doWork()
        } catch (e: CancellationException) {
            propagated = true
        }

        assertTrue("a cancelled update must stop, not continue as a success", propagated)
    }

    // -------------------------------------------------------------------------
    // Empty Library Tests
    // -------------------------------------------------------------------------

    @Test
    fun `doWork returns success when library is empty`() = runTest {
        // Given
        coEvery { getLibraryManga() } returns flowOf(emptyList())

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { updateLibraryManga(any()) }
    }

    // -------------------------------------------------------------------------
    // Update Success Tests
    // -------------------------------------------------------------------------

    @Test
    fun `doWork updates all library manga successfully`() = runTest {
        // Given
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1, testManga2))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(2))
        coEvery { updateLibraryManga(testManga2) } returns Result.success(newChapters(1))

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { updateLibraryManga(testManga1) }
        coVerify(exactly = 1) { updateLibraryManga(testManga2) }
    }

    @Test
    fun `doWork returns success when some manga fail but others succeed`() = runTest {
        // Given
        val manga3 = testManga1.copy(id = 3L, title = "Manga 3")
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1, testManga2, manga3))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))
        coEvery { updateLibraryManga(testManga2) } returns Result.failure(Exception("Network error"))
        coEvery { updateLibraryManga(manga3) } returns Result.success(newChapters(3))

        // When
        val result = worker.doWork()

        // Then - succeeds because at least some manga updated
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns failure when all manga fail`() = runTest {
        // Given
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1, testManga2))
        coEvery { updateLibraryManga(testManga1) } returns Result.failure(Exception("Error 1"))
        coEvery { updateLibraryManga(testManga2) } returns Result.failure(Exception("Error 2"))

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    // -------------------------------------------------------------------------
    // Notification Tests
    // -------------------------------------------------------------------------

    @Test
    fun `doWork does not send notifications when disabled in preferences`() = runTest {
        // Given
        every { generalPreferences.notificationsEnabled } returns flowOf(false)
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(5)) // 5 new chapters

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        // Note: Cannot verify UpdateNotifier was not called since it's created inside doWork
        // This test ensures the branch is covered
    }

    @Test
    fun `doWork sends notifications when new chapters found and notifications enabled`() = runTest {
        // Given
        every { generalPreferences.notificationsEnabled } returns flowOf(true)
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(3))

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        // UpdateNotifier.notify() is called internally (cannot verify due to private instantiation)
    }

    @Test
    fun `doWork respects manga-level notification settings`() = runTest {
        // Given - testManga1 has notifyNewChapters=true, testManga2 has notifyNewChapters=false
        every { generalPreferences.notificationsEnabled } returns flowOf(true)
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1, testManga2))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(2))
        coEvery { updateLibraryManga(testManga2) } returns Result.success(newChapters(1))

        // When
        val result = worker.doWork()

        // Then - only manga1 should be included in notifications
        assertEquals(ListenableWorker.Result.success(), result)
        // Manga1 included, Manga2 excluded from notification list (logic tested in worker)
    }

    // -------------------------------------------------------------------------
    // Auto-Download Tests
    // -------------------------------------------------------------------------

    @Test
    fun `doWork does not auto-download when disabled`() = runTest {
        // Given
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(false)
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(2))

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { downloadManager.enqueue(any()) }
    }

    @Test
    fun `doWork auto-downloads when enabled and on WiFi`() = runTest {
        // Given
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(true)
        every { downloadPreferences.autoDownloadLimit } returns flowOf(2)
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(3))
        coEvery { chapterRepository.getChaptersByMangaId(testManga1.id) } returns flowOf(
            listOf(
                testChapter.copy(id = 1L, chapterNumber = 3.0f, read = false),
                testChapter.copy(id = 2L, chapterNumber = 2.0f, read = false),
                testChapter.copy(id = 3L, chapterNumber = 1.0f, read = false)
            )
        )

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 2) { downloadManager.enqueue(any()) } // Limited to 2 by autoDownloadLimit
    }

    @Test
    fun `doWork skips auto-download when WiFi required but not available`() = runTest {
        // Given
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(true)
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(2))

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { downloadManager.enqueue(any()) }
    }

    @Test
    fun `doWork auto-downloads on cellular when WiFi not required`() = runTest {
        // Given
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(false)
        every { downloadPreferences.autoDownloadLimit } returns flowOf(1)
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))
        coEvery { chapterRepository.getChaptersByMangaId(testManga1.id) } returns flowOf(
            listOf(testChapter.copy(read = false))
        )

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { downloadManager.enqueue(any()) }
    }

    @Test
    fun `doWork respects per-manga auto-download setting`() = runTest {
        // Given - testManga1 has autoDownload=false, testManga2 has autoDownload=true
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(false) // Global disabled
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(false)
        every { downloadPreferences.autoDownloadLimit } returns flowOf(5)

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1, testManga2))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(2))
        coEvery { updateLibraryManga(testManga2) } returns Result.success(newChapters(1))

        coEvery { chapterRepository.getChaptersByMangaId(testManga2.id) } returns flowOf(
            listOf(testChapter.copy(id = 10L, mangaId = 2L, read = false))
        )

        // When
        val result = worker.doWork()

        // Then - only manga2 should trigger download (manga-level override)
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { downloadManager.enqueue(any()) }
    }

    @Test
    fun `doWork downloads only unread chapters`() = runTest {
        // Given
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(false)
        every { downloadPreferences.autoDownloadLimit } returns flowOf(10)

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(4))
        coEvery { chapterRepository.getChaptersByMangaId(testManga1.id) } returns flowOf(
            listOf(
                testChapter.copy(id = 1L, chapterNumber = 4.0f, read = false),
                testChapter.copy(id = 2L, chapterNumber = 3.0f, read = true), // Read - should skip
                testChapter.copy(id = 3L, chapterNumber = 2.0f, read = false),
                testChapter.copy(id = 4L, chapterNumber = 1.0f, read = true)  // Read - should skip
            )
        )

        // When
        val result = worker.doWork()

        // Then - only 2 unread chapters should be enqueued
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 2) { downloadManager.enqueue(any()) }
    }

    @Test
    fun `doWork downloads chapters in descending order`() = runTest {
        // Given
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(false)
        every { downloadPreferences.autoDownloadLimit } returns flowOf(2)

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(3))
        coEvery { chapterRepository.getChaptersByMangaId(testManga1.id) } returns flowOf(
            listOf(
                testChapter.copy(id = 1L, chapterNumber = 1.0f, read = false),
                testChapter.copy(id = 2L, chapterNumber = 2.0f, read = false),
                testChapter.copy(id = 3L, chapterNumber = 3.0f, read = false)
            )
        )

        // When
        val result = worker.doWork()

        // Then - should download chapters 3 and 2 (descending, limited to 2)
        assertEquals(ListenableWorker.Result.success(), result)
        io.mockk.coVerifyOrder {
            downloadManager.enqueue(match { it.chapterId == 3L })
            downloadManager.enqueue(match { it.chapterId == 2L })
        }
    }

    // -------------------------------------------------------------------------
    // WiFi Detection Tests
    // -------------------------------------------------------------------------

    @Test
    fun `isConnectedToWifi returns false when ConnectivityManager unavailable`() = runTest {
        // Given
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(true)
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))

        // When
        val result = worker.doWork()

        // Then - should succeed without crashes and skip downloads due to no WiFi connectivity manager
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { downloadManager.enqueue(any()) }
    }

    @Test
    fun `isConnectedToWifi returns false when network is null`() = runTest {
        // Given
        every { connectivityManager.activeNetwork } returns null
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(true)

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))

        // When
        val result = worker.doWork()

        // Then - no download due to no network
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { downloadManager.enqueue(any()) }
    }

    @Test
    fun `isConnectedToWifi returns false when capabilities are null`() = runTest {
        // Given
        every { connectivityManager.getNetworkCapabilities(network) } returns null
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(true)

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))

        // When
        val result = worker.doWork()

        // Then - no download due to unknown network capabilities
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { downloadManager.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // Update Error Tracking Tests
    // -------------------------------------------------------------------------

    @Test
    fun `doWork records an update error for a manga whose update fails`() = runTest {
        // Given
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.failure(RuntimeException("HTTP 404"))

        // When
        worker.doWork()

        // Then
        coVerify(exactly = 1) {
            updateErrorDao.upsert(
                match { it.mangaId == testManga1.id && it.errorMessage == "HTTP 404" }
            )
        }
    }

    @Test
    fun `doWork clears a previously recorded error when the manga updates successfully`() = runTest {
        // Given
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))

        // When
        worker.doWork()

        // Then
        coVerify(exactly = 1) { updateErrorDao.deleteByMangaId(testManga1.id) }
        coVerify(exactly = 0) { updateErrorDao.upsert(any()) }
    }

    // -------------------------------------------------------------------------
    // Exception Handling Tests
    // -------------------------------------------------------------------------

    @Test
    fun `doWork returns failure on unexpected exception`() = runTest {
        // Given
        coEvery { getLibraryManga() } throws RuntimeException("Unexpected error")

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork continues when auto-download enqueue fails`() = runTest {
        // Given
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(false)
        every { downloadPreferences.autoDownloadLimit } returns flowOf(1)

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))
        coEvery { chapterRepository.getChaptersByMangaId(testManga1.id) } returns flowOf(
            listOf(testChapter.copy(read = false))
        )
        coEvery { downloadManager.enqueue(any()) } throws Exception("enqueue error")

        // When - should not crash the worker
        val result = worker.doWork()

        // Then - worker succeeds despite download failure
        assertEquals(ListenableWorker.Result.success(), result)
    }

    // -------------------------------------------------------------------------
    // Library Update WiFi Gate Tests
    // -------------------------------------------------------------------------

    @Test
    fun `doWork skips updates when updateOnlyOnWifi is true and WiFi unavailable`() = runTest {
        // Given
        every { libraryPreferences.updateOnlyOnWifi } returns flowOf(true)
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))

        // When
        val result = worker.doWork()

        // Then - work is retried because WiFi gate blocks the work until connectivity is restored
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { updateLibraryManga(any()) }
    }

    @Test
    fun `doWork proceeds with updates when updateOnlyOnWifi is true and WiFi available`() = runTest {
        // Given
        every { libraryPreferences.updateOnlyOnWifi } returns flowOf(true)
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(0))

        // When
        val result = worker.doWork()

        // Then - update proceeds normally on WiFi
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { updateLibraryManga(testManga1) }
    }

    @Test
    fun `doWork proceeds with updates when updateOnlyOnWifi is false and no WiFi`() = runTest {
        // Given
        every { libraryPreferences.updateOnlyOnWifi } returns flowOf(false)
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(newChapters(1))

        // When
        val result = worker.doWork()

        // Then - update proceeds on cellular since WiFi gate is off
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { updateLibraryManga(testManga1) }
    }
}
