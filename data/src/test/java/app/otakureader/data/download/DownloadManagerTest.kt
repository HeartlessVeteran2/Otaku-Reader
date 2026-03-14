package app.otakureader.data.download

import android.content.Context
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.domain.model.DownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for DownloadManager state transitions and queue management.
 * Tests pause, resume, cancel, remove, and clearAll operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var downloader: Downloader
    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var downloadManager: DownloadManager

    private val testRequest = ChapterDownloadRequest(
        mangaId = 1L,
        chapterId = 100L,
        sourceName = "TestSource",
        mangaTitle = "Test Manga",
        chapterTitle = "Chapter 1",
        pageUrls = listOf("https://example.com/page1.jpg", "https://example.com/page2.jpg")
    )

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        downloader = mockk(relaxed = true)
        downloadPreferences = mockk()

        // Mock preferences
        every { downloadPreferences.saveAsCbz } returns flowOf(false)

        // Mock successful download
        coEvery { downloader.downloadPage(any(), any()) } returns Result.success(Unit)

        // Mock file operations
        every { context.getExternalFilesDir(null) } returns File("/tmp/test")

        downloadManager = DownloadManager(context, downloader, downloadPreferences)
    }

    // -------------------------------------------------------------------------
    // Enqueue Tests
    // -------------------------------------------------------------------------

    @Test
    fun `enqueue adds new download to queue`() = runTest(testDispatcher) {
        // When
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertEquals(1, downloads.size)
        assertEquals(testRequest.chapterId, downloads[0].chapterId)
        assertEquals(testRequest.mangaTitle, downloads[0].mangaTitle)
        assertEquals(testRequest.chapterTitle, downloads[0].chapterTitle)
    }

    @Test
    fun `enqueue does not duplicate active download`() = runTest(testDispatcher) {
        // Given - first enqueue
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        // When - try to enqueue same chapter again
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        // Then - should still have only one item
        val downloads = downloadManager.downloads.first()
        assertEquals(1, downloads.size)
    }

    @Test
    fun `enqueue allows re-queueing completed download`() = runTest(testDispatcher) {
        // Given - download that completed
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        // Wait for download to complete
        var downloads = downloadManager.downloads.first()
        while (downloads.firstOrNull()?.status != DownloadStatus.COMPLETED) {
            advanceUntilIdle()
            downloads = downloadManager.downloads.first()
        }

        // When - enqueue same chapter again
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        // Then - download should be re-queued
        downloads = downloadManager.downloads.first()
        assertEquals(1, downloads.size)
        assertTrue(
            downloads[0].status == DownloadStatus.QUEUED ||
            downloads[0].status == DownloadStatus.DOWNLOADING
        )
    }

    // -------------------------------------------------------------------------
    // Pause Tests
    // -------------------------------------------------------------------------

    @Test
    fun `pause transitions active download to PAUSED`() = runTest(testDispatcher) {
        // Given - active download
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        // When
        downloadManager.pause(testRequest.chapterId)
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertEquals(1, downloads.size)
        assertEquals(DownloadStatus.PAUSED, downloads[0].status)
    }

    @Test
    fun `pause on non-existent download does nothing`() = runTest(testDispatcher) {
        // When
        downloadManager.pause(999L)
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertTrue(downloads.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Resume Tests
    // -------------------------------------------------------------------------

    @Test
    fun `resume restarts paused download`() = runTest(testDispatcher) {
        // Given - paused download
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()
        downloadManager.pause(testRequest.chapterId)
        advanceUntilIdle()

        var downloads = downloadManager.downloads.first()
        assertEquals(DownloadStatus.PAUSED, downloads[0].status)

        // When
        downloadManager.resume(testRequest.chapterId)
        advanceUntilIdle()

        // Then - should transition to DOWNLOADING or COMPLETED
        downloads = downloadManager.downloads.first()
        assertTrue(
            downloads[0].status == DownloadStatus.DOWNLOADING ||
            downloads[0].status == DownloadStatus.COMPLETED
        )
    }

    @Test
    fun `resume on non-paused download does nothing`() = runTest(testDispatcher) {
        // Given - active download
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        val beforeResume = downloadManager.downloads.first()[0].status

        // When
        downloadManager.resume(testRequest.chapterId)
        advanceUntilIdle()

        // Then - status unchanged
        val afterResume = downloadManager.downloads.first()[0].status
        assertEquals(beforeResume, afterResume)
    }

    // -------------------------------------------------------------------------
    // Cancel Tests
    // -------------------------------------------------------------------------

    @Test
    fun `cancel removes download from queue`() = runTest(testDispatcher) {
        // Given
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        assertEquals(1, downloadManager.downloads.first().size)

        // When
        downloadManager.cancel(testRequest.chapterId)
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertTrue(downloads.isEmpty())
    }

    @Test
    fun `cancel removes request so resume does not work`() = runTest(testDispatcher) {
        // Given
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()
        downloadManager.cancel(testRequest.chapterId)
        advanceUntilIdle()

        // When - try to resume canceled download
        downloadManager.resume(testRequest.chapterId)
        advanceUntilIdle()

        // Then - should remain empty (cannot resume canceled download)
        val downloads = downloadManager.downloads.first()
        assertTrue(downloads.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Remove Tests
    // -------------------------------------------------------------------------

    @Test
    fun `remove deletes download metadata`() = runTest(testDispatcher) {
        // Given
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        // When
        downloadManager.remove(testRequest.chapterId)
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertTrue(downloads.isEmpty())
    }

    @Test
    fun `remove on non-existent download does nothing`() = runTest(testDispatcher) {
        // When
        downloadManager.remove(999L)
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertTrue(downloads.isEmpty())
    }

    // -------------------------------------------------------------------------
    // ClearAll Tests
    // -------------------------------------------------------------------------

    @Test
    fun `clearAll removes all downloads`() = runTest(testDispatcher) {
        // Given - multiple downloads
        val request1 = testRequest.copy(chapterId = 1L)
        val request2 = testRequest.copy(chapterId = 2L)
        val request3 = testRequest.copy(chapterId = 3L)

        downloadManager.enqueue(request1)
        downloadManager.enqueue(request2)
        downloadManager.enqueue(request3)
        advanceUntilIdle()

        assertEquals(3, downloadManager.downloads.first().size)

        // When
        downloadManager.clearAll()
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertTrue(downloads.isEmpty())
    }

    @Test
    fun `clearAll on empty queue does nothing`() = runTest(testDispatcher) {
        // When
        downloadManager.clearAll()
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertTrue(downloads.isEmpty())
    }

    // -------------------------------------------------------------------------
    // State Transition Tests
    // -------------------------------------------------------------------------

    @Test
    fun `download transitions from QUEUED to DOWNLOADING to COMPLETED`() = runTest(testDispatcher) {
        // Given
        downloadManager.enqueue(testRequest)

        // Initially QUEUED
        advanceUntilIdle()
        var download = downloadManager.downloads.first().firstOrNull()

        // Should eventually reach DOWNLOADING or COMPLETED
        while (download?.status == DownloadStatus.QUEUED) {
            advanceUntilIdle()
            download = downloadManager.downloads.first().firstOrNull()
        }

        assertTrue(
            download?.status == DownloadStatus.DOWNLOADING ||
            download?.status == DownloadStatus.COMPLETED
        )

        // Wait for completion
        while (download?.status == DownloadStatus.DOWNLOADING) {
            advanceUntilIdle()
            download = downloadManager.downloads.first().firstOrNull()
        }

        assertEquals(DownloadStatus.COMPLETED, download?.status)
    }

    @Test
    fun `paused download retains request for resume`() = runTest(testDispatcher) {
        // Given
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()

        // When - pause and check state
        downloadManager.pause(testRequest.chapterId)
        advanceUntilIdle()

        var downloads = downloadManager.downloads.first()
        assertEquals(DownloadStatus.PAUSED, downloads[0].status)

        // Then - resume should work
        downloadManager.resume(testRequest.chapterId)
        advanceUntilIdle()

        downloads = downloadManager.downloads.first()
        assertTrue(
            downloads[0].status == DownloadStatus.DOWNLOADING ||
            downloads[0].status == DownloadStatus.COMPLETED
        )
    }

    @Test
    fun `multiple downloads can be managed independently`() = runTest(testDispatcher) {
        // Given
        val request1 = testRequest.copy(chapterId = 1L, chapterTitle = "Chapter 1")
        val request2 = testRequest.copy(chapterId = 2L, chapterTitle = "Chapter 2")
        val request3 = testRequest.copy(chapterId = 3L, chapterTitle = "Chapter 3")

        downloadManager.enqueue(request1)
        downloadManager.enqueue(request2)
        downloadManager.enqueue(request3)
        advanceUntilIdle()

        // When - pause first, cancel second, leave third active
        downloadManager.pause(1L)
        downloadManager.cancel(2L)
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertEquals(2, downloads.size) // Only 1 and 3 remain

        val download1 = downloads.find { it.chapterId == 1L }
        val download3 = downloads.find { it.chapterId == 3L }

        assertEquals(DownloadStatus.PAUSED, download1?.status)
        assertTrue(
            download3?.status == DownloadStatus.QUEUED ||
            download3?.status == DownloadStatus.DOWNLOADING ||
            download3?.status == DownloadStatus.COMPLETED
        )
    }

    // -------------------------------------------------------------------------
    // Empty PageUrls Tests
    // -------------------------------------------------------------------------

    @Test
    fun `enqueue with empty pageUrls keeps download QUEUED`() = runTest(testDispatcher) {
        // Given - request with no pages
        val emptyRequest = testRequest.copy(pageUrls = emptyList())

        // When
        downloadManager.enqueue(emptyRequest)
        advanceUntilIdle()

        // Then - should stay QUEUED (not fail)
        val downloads = downloadManager.downloads.first()
        assertEquals(1, downloads.size)
        assertEquals(DownloadStatus.QUEUED, downloads[0].status)
    }
}
