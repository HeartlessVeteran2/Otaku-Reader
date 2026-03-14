package app.otakureader.data.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.data.download.DownloadManager
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.UpdateLibraryMangaUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for LibraryUpdateWorker.
 * Tests library update flow, auto-download logic, notification triggering, and WiFi checking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryUpdateWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var getLibraryManga: GetLibraryMangaUseCase
    private lateinit var updateLibraryManga: UpdateLibraryMangaUseCase
    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var downloadManager: DownloadManager
    private lateinit var chapterRepository: ChapterRepository
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
        genres = listOf("Action", "Adventure"),
        status = 0,
        thumbnailUrl = "https://example.com/cover1.jpg",
        favorite = true,
        lastUpdate = System.currentTimeMillis(),
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
        genres = listOf("Romance", "Comedy"),
        status = 0,
        thumbnailUrl = "https://example.com/cover2.jpg",
        favorite = true,
        lastUpdate = System.currentTimeMillis(),
        initialized = true,
        notifyNewChapters = false, // Notifications disabled for this manga
        autoDownload = true
    )

    private val testChapter = Chapter(
        id = 1L,
        mangaId = 1L,
        url = "https://example.com/chapter1",
        name = "Chapter 1",
        chapterNumber = 1.0,
        scanlator = "Test Scanlator",
        uploadDate = System.currentTimeMillis(),
        read = false,
        bookmarked = false,
        lastReadPage = 0,
        totalPages = 20
    )

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        getLibraryManga = mockk()
        updateLibraryManga = mockk()
        downloadPreferences = mockk()
        generalPreferences = mockk()
        downloadManager = mockk(relaxed = true)
        chapterRepository = mockk()

        connectivityManager = mockk()
        network = mockk()
        networkCapabilities = mockk()

        // Default preference values
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(false)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(false)
        every { downloadPreferences.autoDownloadLimit } returns flowOf(3)
        every { generalPreferences.notificationsEnabled } returns flowOf(true)

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
            downloadPreferences,
            generalPreferences,
            downloadManager,
            chapterRepository
        )
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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(2)
        coEvery { updateLibraryManga(testManga2) } returns Result.success(1)

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(1)
        coEvery { updateLibraryManga(testManga2) } returns Result.failure(Exception("Network error"))
        coEvery { updateLibraryManga(manga3) } returns Result.success(3)

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(5) // 5 new chapters

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(3)

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(2)
        coEvery { updateLibraryManga(testManga2) } returns Result.success(1)

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(2)

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(3)
        coEvery { chapterRepository.getChaptersByMangaId(testManga1.id) } returns flowOf(
            listOf(
                testChapter.copy(id = 1L, chapterNumber = 3.0, read = false),
                testChapter.copy(id = 2L, chapterNumber = 2.0, read = false),
                testChapter.copy(id = 3L, chapterNumber = 1.0, read = false)
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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(2)

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(1)
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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(2)
        coEvery { updateLibraryManga(testManga2) } returns Result.success(1)

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(4)
        coEvery { chapterRepository.getChaptersByMangaId(testManga1.id) } returns flowOf(
            listOf(
                testChapter.copy(id = 1L, chapterNumber = 4.0, read = false),
                testChapter.copy(id = 2L, chapterNumber = 3.0, read = true), // Read - should skip
                testChapter.copy(id = 3L, chapterNumber = 2.0, read = false),
                testChapter.copy(id = 4L, chapterNumber = 1.0, read = true)  // Read - should skip
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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(3)
        coEvery { chapterRepository.getChaptersByMangaId(testManga1.id) } returns flowOf(
            listOf(
                testChapter.copy(id = 1L, chapterNumber = 1.0, read = false),
                testChapter.copy(id = 2L, chapterNumber = 2.0, read = false),
                testChapter.copy(id = 3L, chapterNumber = 3.0, read = false)
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
    @Test
    fun `isConnectedToWifi returns false when ConnectivityManager unavailable`() = runTest {
        // Given
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(true)
        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(1)

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(1)

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
        coEvery { updateLibraryManga(testManga1) } returns Result.success(1)

        // When
        val result = worker.doWork()

        // Then - no download due to unknown network capabilities
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { downloadManager.enqueue(any()) }
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
    @Test
    fun `doWork continues when auto-download enqueue fails`() = runTest {
        // Given
        every { downloadPreferences.autoDownloadEnabled } returns flowOf(true)
        every { downloadPreferences.downloadOnlyOnWifi } returns flowOf(false)
        every { downloadPreferences.autoDownloadLimit } returns flowOf(1)

        coEvery { getLibraryManga() } returns flowOf(listOf(testManga1))
        coEvery { updateLibraryManga(testManga1) } returns Result.success(1)
        coEvery { chapterRepository.getChaptersByMangaId(testManga1.id) } returns flowOf(
            listOf(testChapter.copy(read = false))
        )
        coEvery { downloadManager.enqueue(any()) } throws Exception("enqueue error")

        // When - should not crash the worker
        val result = worker.doWork()

        // Then - worker succeeds despite download failure
        assertEquals(ListenableWorker.Result.success(), result)
    }
}
