package app.otakureader.data.download

import android.content.Context
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.domain.model.DownloadStatus
import app.otakureader.domain.model.DownloadPriority
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
        every { downloadPreferences.concurrentDownloads } returns flowOf(2)

        // Mock successful download
        coEvery { downloader.downloadPage(any(), any()) } returns Result.success(File("/tmp/test/page.jpg"))

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

        // Then - download should be re-queued (and may have already completed again)
        downloads = downloadManager.downloads.first()
        assertEquals(1, downloads.size)
        assertTrue(
            downloads[0].status == DownloadStatus.QUEUED ||
            downloads[0].status == DownloadStatus.DOWNLOADING ||
            downloads[0].status == DownloadStatus.COMPLETED
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

        // Poll until the IO job completes and sets the status back to QUEUED
        var downloads = downloadManager.downloads.first()
        while (downloads.firstOrNull()?.status == DownloadStatus.DOWNLOADING) {
            advanceUntilIdle()
            downloads = downloadManager.downloads.first()
        }

        // Then - should stay QUEUED (not fail)
        assertEquals(1, downloads.size)
        assertEquals(DownloadStatus.QUEUED, downloads[0].status)
    }

    // -------------------------------------------------------------------------
    // Prioritization Tests
    // -------------------------------------------------------------------------

    @Test
    fun `enqueue respects explicit priority from request`() = runTest(testDispatcher) {
        // When
        downloadManager.enqueue(testRequest.copy(chapterId = 1L, priority = -50))
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertEquals(1, downloads.size)
        assertEquals(-50, downloads[0].priority)
    }

    @Test
    fun `prioritize moves item ahead of normal-priority items`() = runTest(testDispatcher) {
        // Given - two chapters with default priority
        val request1 = testRequest.copy(chapterId = 10L, chapterTitle = "Chapter 10", pageUrls = emptyList())
        val request2 = testRequest.copy(chapterId = 20L, chapterTitle = "Chapter 20", pageUrls = emptyList())

        downloadManager.enqueue(request1)
        downloadManager.enqueue(request2)
        advanceUntilIdle()

        // When - prioritize the second chapter
        downloadManager.prioritize(20L)
        advanceUntilIdle()

        // Then - chapter 20 should appear first (lower priority value)
        val downloads = downloadManager.downloads.first()
        assertEquals(2, downloads.size)
        assertEquals(20L, downloads[0].chapterId)
        assertTrue(downloads[0].priority < downloads[1].priority)
    }

    @Test
    fun `prioritize on non-existent chapter is a no-op`() = runTest(testDispatcher) {
        // Given
        downloadManager.enqueue(testRequest)
        advanceUntilIdle()
        val before = downloadManager.downloads.first()

        // When
        downloadManager.prioritize(999L)
        advanceUntilIdle()

        // Then - queue unchanged
        val after = downloadManager.downloads.first()
        assertEquals(before.size, after.size)
        assertEquals(before[0].priority, after[0].priority)
    }

    @Test
    fun `reorder sets explicit priority on queued item`() = runTest(testDispatcher) {
        // Given
        val emptyRequest = testRequest.copy(pageUrls = emptyList())
        downloadManager.enqueue(emptyRequest)
        advanceUntilIdle()

        // When
        downloadManager.reorder(testRequest.chapterId, 42)
        advanceUntilIdle()

        // Then
        val downloads = downloadManager.downloads.first()
        assertEquals(1, downloads.size)
        assertEquals(42, downloads[0].priority)
    }

    @Test
    fun `downloads list is sorted by priority ascending`() = runTest(testDispatcher) {
        // Given - three chapters with different priorities, all with empty pages to stay QUEUED
        val low = testRequest.copy(chapterId = 1L, pageUrls = emptyList(), priority = 100)
        val high = testRequest.copy(chapterId = 2L, pageUrls = emptyList(), priority = -100)
        val normal = testRequest.copy(chapterId = 3L, pageUrls = emptyList(), priority = 0)

        // Enqueue in low → normal → high priority order
        downloadManager.enqueue(low)
        downloadManager.enqueue(normal)
        downloadManager.enqueue(high)
        advanceUntilIdle()

        // Then - emitted list should be sorted: high (-100), normal (0), low (100)
        val downloads = downloadManager.downloads.first()
        assertEquals(3, downloads.size)
        assertEquals(2L, downloads[0].chapterId) // priority -100
        assertEquals(3L, downloads[1].chapterId) // priority 0
        assertEquals(1L, downloads[2].chapterId) // priority 100
    }

    @Test
    fun `equal-priority items maintain FIFO insertion order`() = runTest(testDispatcher) {
        // Given - three chapters all with default priority and no pages (stay QUEUED)
        val r1 = testRequest.copy(chapterId = 1L, pageUrls = emptyList())
        val r2 = testRequest.copy(chapterId = 2L, pageUrls = emptyList())
        val r3 = testRequest.copy(chapterId = 3L, pageUrls = emptyList())

        downloadManager.enqueue(r1)
        downloadManager.enqueue(r2)
        downloadManager.enqueue(r3)
        advanceUntilIdle()

        // Then - insertion order preserved for equal priorities
        val downloads = downloadManager.downloads.first()
        assertEquals(listOf(1L, 2L, 3L), downloads.map { it.chapterId })
    }

    // -------------------------------------------------------------------------
    // prioritizeAll Tests
    // -------------------------------------------------------------------------

    @Test
    fun `prioritizeAll moves targets before non-targets`() = runTest(testDispatcher) {
        // Given - four chapters in default priority order (all with no pages to stay QUEUED)
        for (id in 1L..4L) {
            downloadManager.enqueue(testRequest.copy(chapterId = id, pageUrls = emptyList()))
        }
        advanceUntilIdle()

        // When - bulk-prioritize chapters 3 and 4
        downloadManager.prioritizeAll(listOf(3L, 4L))
        advanceUntilIdle()

        // Then - chapters 3 and 4 appear before 1 and 2
        val ids = downloadManager.downloads.first().map { it.chapterId }
        val indexOf3 = ids.indexOf(3L)
        val indexOf4 = ids.indexOf(4L)
        val indexOf1 = ids.indexOf(1L)
        val indexOf2 = ids.indexOf(2L)
        assertTrue("3 should come before 1", indexOf3 < indexOf1)
        assertTrue("3 should come before 2", indexOf3 < indexOf2)
        assertTrue("4 should come before 1", indexOf4 < indexOf1)
        assertTrue("4 should come before 2", indexOf4 < indexOf2)
    }

    @Test
    fun `prioritizeAll preserves relative order of targets`() = runTest(testDispatcher) {
        // Given - five chapters enqueued with ascending priorities so queue order is 1,2,3,4,5
        for (id in 1L..5L) {
            downloadManager.enqueue(
                testRequest.copy(chapterId = id, pageUrls = emptyList(), priority = id.toInt())
            )
        }
        advanceUntilIdle()

        // When - bulk-prioritize chapters 5, 2, 4 (in arbitrary caller order)
        downloadManager.prioritizeAll(listOf(5L, 2L, 4L))
        advanceUntilIdle()

        // Then - targets appear before non-targets AND retain their relative queue order (2,4,5)
        val ids = downloadManager.downloads.first().map { it.chapterId }
        // Non-target IDs 1 and 3 come after the three targets
        val firstNonTargetIndex = ids.indexOfFirst { it == 1L || it == 3L }
        val lastTargetIndex = ids.indexOfLast { it == 2L || it == 4L || it == 5L }
        assertTrue("All targets should be before non-targets", lastTargetIndex < firstNonTargetIndex)
        // Relative queue order among targets: 2 before 4 before 5
        assertTrue(ids.indexOf(2L) < ids.indexOf(4L))
        assertTrue(ids.indexOf(4L) < ids.indexOf(5L))
    }

    @Test
    fun `prioritizeAll ignores IDs not in queue`() = runTest(testDispatcher) {
        // Given
        downloadManager.enqueue(testRequest.copy(chapterId = 1L, pageUrls = emptyList()))
        downloadManager.enqueue(testRequest.copy(chapterId = 2L, pageUrls = emptyList()))
        advanceUntilIdle()

        val before = downloadManager.downloads.first()

        // When - include an ID (999) that is not in the queue
        downloadManager.prioritizeAll(listOf(999L))
        advanceUntilIdle()

        // Then - queue unchanged
        val after = downloadManager.downloads.first()
        assertEquals(before.map { it.chapterId }, after.map { it.chapterId })
        assertEquals(before.map { it.priority }, after.map { it.priority })
    }

    @Test
    fun `prioritizeAll with empty list is a no-op`() = runTest(testDispatcher) {
        // Given
        downloadManager.enqueue(testRequest.copy(chapterId = 1L, pageUrls = emptyList()))
        advanceUntilIdle()
        val before = downloadManager.downloads.first()

        // When
        downloadManager.prioritizeAll(emptyList())
        advanceUntilIdle()

        // Then - unchanged
        val after = downloadManager.downloads.first()
        assertEquals(before.map { it.chapterId }, after.map { it.chapterId })
    }

    @Test
    fun `prioritizeAll handles near Int-MIN_VALUE priorities correctly`() = runTest(testDispatcher) {
        // Given - one target already near Int.MIN_VALUE, two non-targets with NORMAL priority
        val nearMin = testRequest.copy(chapterId = 1L, pageUrls = emptyList(), priority = Int.MIN_VALUE + 1)
        val normal1 = testRequest.copy(chapterId = 2L, pageUrls = emptyList(), priority = 0)
        val normal2 = testRequest.copy(chapterId = 3L, pageUrls = emptyList(), priority = 1)

        downloadManager.enqueue(nearMin)
        downloadManager.enqueue(normal1)
        downloadManager.enqueue(normal2)
        advanceUntilIdle()

        // When - prioritize the already-near-min item AND normal1
        downloadManager.prioritizeAll(listOf(1L, 2L))
        advanceUntilIdle()

        // Then - both prioritized items appear before normal2, and none have crashed
        val downloads = downloadManager.downloads.first()
        assertEquals(3, downloads.size)
        val idx1 = downloads.indexOfFirst { it.chapterId == 1L }
        val idx2 = downloads.indexOfFirst { it.chapterId == 2L }
        val idx3 = downloads.indexOfFirst { it.chapterId == 3L }
        assertTrue("chapter 1 before chapter 3", idx1 < idx3)
        assertTrue("chapter 2 before chapter 3", idx2 < idx3)
        // All prioritized items must have strictly lower priority than non-targets
        assertTrue(downloads[idx1].priority < downloads[idx3].priority)
        assertTrue(downloads[idx2].priority < downloads[idx3].priority)
    }

    @Test
    fun `prioritizeAll when all items are targets leaves them all present`() = runTest(testDispatcher) {
        // Given
        for (id in 1L..3L) {
            downloadManager.enqueue(testRequest.copy(chapterId = id, pageUrls = emptyList()))
        }
        advanceUntilIdle()

        // When - target every item
        downloadManager.prioritizeAll(listOf(1L, 2L, 3L))
        advanceUntilIdle()

        // Then - all items still present
        val downloads = downloadManager.downloads.first()
        assertEquals(3, downloads.size)
        assertEquals(setOf(1L, 2L, 3L), downloads.map { it.chapterId }.toSet())
    }
}

