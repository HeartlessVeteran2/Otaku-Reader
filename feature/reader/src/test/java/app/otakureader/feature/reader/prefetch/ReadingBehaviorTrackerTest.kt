package app.otakureader.feature.reader.prefetch

import app.otakureader.domain.model.PageNavigationEvent
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReadingBehaviorTrackerTest {

    private lateinit var tracker: ReadingBehaviorTracker

    @Before
    fun setup() {
        tracker = ReadingBehaviorTracker()
    }

    @Test
    fun `initial behavior should be default`() {
        val behavior = tracker.currentBehavior

        assertEquals(0.9f, behavior.forwardNavigationRatio, 0.01f)
        assertEquals(5000L, behavior.averagePageDurationMs)
        assertEquals(0, behavior.sampleSize)
    }

    @Test
    fun `recording navigation updates sample size`() {
        // Given
        val event = createNavigationEvent(fromPage = 0, toPage = 1)

        // When
        tracker.recordNavigation(event)

        // Then
        assertEquals(1, tracker.getSampleSize())
    }

    @Test
    fun `forward navigation increases forward ratio`() = runTest {
        // Given: Record 100 forward navigations
        repeat(100) { i ->
            val event = createNavigationEvent(fromPage = i, toPage = i + 1)
            tracker.recordNavigation(event)
        }

        // When
        val behavior = tracker.currentBehavior

        // Then
        assertTrue(behavior.forwardNavigationRatio > 0.95f)
        assertTrue(behavior.isPrimaryForwardReader)
    }

    @Test
    fun `backward navigation decreases forward ratio`() = runTest {
        // Given: Record 50 forward and 50 backward navigations
        repeat(50) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i, toPage = i + 1))
        }
        repeat(50) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i + 1, toPage = i))
        }

        // When
        val behavior = tracker.currentBehavior

        // Then
        assertEquals(0.5f, behavior.forwardNavigationRatio, 0.1f)
        assertFalse(behavior.isPrimaryForwardReader)
    }

    @Test
    fun `sequential navigation updates sequential ratio`() = runTest {
        // Given: Record 100 sequential navigations
        repeat(100) { i ->
            val event = createNavigationEvent(fromPage = i, toPage = i + 1)
            tracker.recordNavigation(event)
        }

        // When
        val behavior = tracker.currentBehavior

        // Then
        assertEquals(1.0f, behavior.sequentialNavigationRatio, 0.01f)
    }

    @Test
    fun `jump navigation decreases sequential ratio`() = runTest {
        // Given: Record 50 sequential and 50 jump navigations
        repeat(50) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i, toPage = i + 1))
        }
        repeat(50) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i, toPage = i + 10))
        }

        // When
        val behavior = tracker.currentBehavior

        // Then
        assertEquals(0.5f, behavior.sequentialNavigationRatio, 0.1f)
        assertTrue(behavior.isUnpredictable)
    }

    @Test
    fun `average page duration is calculated correctly`() = runTest {
        // Given: Record events with varying durations
        tracker.recordNavigation(createNavigationEvent(fromPage = 0, toPage = 1, durationMs = 3000))
        tracker.recordNavigation(createNavigationEvent(fromPage = 1, toPage = 2, durationMs = 5000))
        tracker.recordNavigation(createNavigationEvent(fromPage = 2, toPage = 3, durationMs = 7000))

        // Need at least 50 samples for meaningful statistics
        repeat(47) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i + 3, toPage = i + 4, durationMs = 5000))
        }

        // When
        val behavior = tracker.currentBehavior

        // Then
        // Average should be close to 5000ms
        assertTrue(behavior.averagePageDurationMs in 4500L..5500L)
    }

    @Test
    fun `outlier durations are excluded from average`() = runTest {
        // Given: Record events with outliers
        tracker.recordNavigation(createNavigationEvent(fromPage = 0, toPage = 1, durationMs = 50)) // Too short
        tracker.recordNavigation(createNavigationEvent(fromPage = 1, toPage = 2, durationMs = 80000)) // Too long

        // Add 48 normal events
        repeat(48) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i + 2, toPage = i + 3, durationMs = 5000))
        }

        // When
        val behavior = tracker.currentBehavior

        // Then
        // Outliers should be excluded, average should be around 5000ms
        assertTrue(behavior.averagePageDurationMs in 4500L..5500L)
    }

    @Test
    fun `preferred reader mode is detected`() = runTest {
        // Given: Record 100 events in webtoon mode (mode = 2)
        repeat(100) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i, toPage = i + 1, readerMode = 2))
        }

        // When
        val behavior = tracker.currentBehavior

        // Then
        assertEquals(2, behavior.preferredReaderMode)
    }

    @Test
    fun `behavior is unpredictable with low sample size`() {
        // Given: Record only 10 events
        repeat(10) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i, toPage = i + 1))
        }

        // When
        val behavior = tracker.currentBehavior

        // Then
        assertTrue(behavior.isUnpredictable)
    }

    @Test
    fun `behavior is predictable with sufficient sample size`() {
        // Given: Record 100 sequential forward events
        repeat(100) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i, toPage = i + 1))
        }

        // When
        val behavior = tracker.currentBehavior

        // Then
        assertFalse(behavior.isUnpredictable)
    }

    @Test
    fun `reset clears all navigation history`() {
        // Given: Record some events
        repeat(50) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i, toPage = i + 1))
        }
        assertEquals(50, tracker.getSampleSize())

        // When
        tracker.reset()

        // Then
        assertEquals(0, tracker.getSampleSize())
        val behavior = tracker.currentBehavior
        assertEquals(0, behavior.sampleSize)
    }

    @Test
    fun `rolling window maintains max size`() {
        // Given: Record more than MAX_HISTORY_SIZE events (500)
        repeat(600) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i, toPage = i + 1))
        }

        // When
        val sampleSize = tracker.getSampleSize()

        // Then
        assertEquals(500, sampleSize)
    }

    @Test
    fun `getBehaviorForManga returns current behavior`() {
        // Given
        repeat(100) { i ->
            tracker.recordNavigation(createNavigationEvent(fromPage = i, toPage = i + 1, mangaId = 123))
        }

        // When
        val behavior = tracker.getBehaviorForManga(123)

        // Then
        assertEquals(tracker.currentBehavior, behavior)
    }

    // Helper function to create navigation events
    private fun createNavigationEvent(
        mangaId: Long = 1L,
        chapterId: Long = 1L,
        fromPage: Int = 0,
        toPage: Int = 1,
        durationMs: Long = 5000L,
        readerMode: Int = 0,
        timestamp: Long = System.currentTimeMillis()
    ) = PageNavigationEvent(
        mangaId = mangaId,
        chapterId = chapterId,
        fromPage = fromPage,
        toPage = toPage,
        pageDurationMs = durationMs,
        readerMode = readerMode,
        timestamp = timestamp
    )
}
