package app.otakureader.feature.reader.prefetch

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.otakureader.domain.model.PrefetchStrategy
import app.otakureader.domain.model.ReadingBehavior
import app.otakureader.feature.reader.model.ReaderPage
import coil3.ImageLoader
import coil3.request.ImageRequest
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmartPrefetchManagerTest {

    private lateinit var context: Context
    private lateinit var imageLoader: ImageLoader
    private lateinit var behaviorTracker: ReadingBehaviorTracker
    private lateinit var prefetchManager: SmartPrefetchManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        imageLoader = mockk(relaxed = true)
        behaviorTracker = mockk(relaxed = true)

        // Mock connectivity manager
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val networkCapabilities = mockk<NetworkCapabilities>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns mockk()
        every { connectivityManager.getNetworkCapabilities(any()) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true

        prefetchManager = SmartPrefetchManager(context, imageLoader)
    }

    @Test
    fun `initial cache hit rate is zero`() {
        assertEquals(0f, prefetchManager.getCacheHitRate(), 0.01f)
    }

    @Test
    fun `initial prefetch efficiency is zero`() {
        assertEquals(0f, prefetchManager.getPrefetchEfficiency(), 0.01f)
    }

    @Test
    fun `prefetchPages uses conservative strategy correctly`() = runTest(testDispatcher) {
        // Given
        val pages = createTestPages(10)
        val currentPage = 5
        val behavior = ReadingBehavior.DEFAULT

        // When
        prefetchManager.prefetchPages(
            pages = pages,
            currentPage = currentPage,
            strategy = PrefetchStrategy.Conservative,
            behavior = behavior,
            onlyOnWiFi = false,
            scope = CoroutineScope(testDispatcher)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Conservative should prefetch 0 before, 1-2 after
        verify(atLeast = 1, atMost = 2) { imageLoader.enqueue(any()) }
    }

    @Test
    fun `prefetchPages uses balanced strategy correctly`() = runTest(testDispatcher) {
        // Given
        val pages = createTestPages(10)
        val currentPage = 5
        val behavior = ReadingBehavior.DEFAULT

        // When
        prefetchManager.prefetchPages(
            pages = pages,
            currentPage = currentPage,
            strategy = PrefetchStrategy.Balanced,
            behavior = behavior,
            onlyOnWiFi = false,
            scope = CoroutineScope(testDispatcher)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Balanced should prefetch 1 before, 3 after = 4 total
        verify(exactly = 4) { imageLoader.enqueue(any()) }
    }

    @Test
    fun `prefetchPages uses aggressive strategy correctly`() = runTest(testDispatcher) {
        // Given
        val pages = createTestPages(20)
        val currentPage = 10
        val behavior = ReadingBehavior.DEFAULT

        // When
        prefetchManager.prefetchPages(
            pages = pages,
            currentPage = currentPage,
            strategy = PrefetchStrategy.Aggressive,
            behavior = behavior,
            onlyOnWiFi = false,
            scope = CoroutineScope(testDispatcher)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Aggressive should prefetch 2 before, 7 after = 9 total
        verify(exactly = 9) { imageLoader.enqueue(any()) }
    }

    @Test
    fun `prefetchPages skips on mobile data when onlyOnWiFi is true`() = runTest(testDispatcher) {
        // Given: Mock mobile data connection
        val networkCapabilities = mockk<NetworkCapabilities>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns mockk()
        every { connectivityManager.getNetworkCapabilities(any()) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // Recreate manager with new context
        val manager = SmartPrefetchManager(context, imageLoader)

        val pages = createTestPages(10)

        // When
        manager.prefetchPages(
            pages = pages,
            currentPage = 5,
            strategy = PrefetchStrategy.Balanced,
            behavior = ReadingBehavior.DEFAULT,
            onlyOnWiFi = true,
            scope = CoroutineScope(testDispatcher)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: No prefetch should occur
        verify(exactly = 0) { imageLoader.enqueue(any()) }
    }

    @Test
    fun `recordPageView updates cache hits when page was prefetched`() = runTest(testDispatcher) {
        // Given: Simulate a prefetch by calling prefetchPages first
        val pages = createTestPages(5)
        every { behaviorTracker.getBehaviorForManga(any()) } returns ReadingBehavior.DEFAULT

        prefetchManager.prefetchPages(
            pages = pages,
            currentPage = 0,
            strategy = PrefetchStrategy.Balanced,
            behavior = ReadingBehavior.DEFAULT,
            onlyOnWiFi = false,
            scope = CoroutineScope(testDispatcher)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Record viewing a prefetched page
        prefetchManager.recordPageView(pages[1])

        // Then: Cache hit rate should increase
        assertTrue(prefetchManager.getCacheHitRate() > 0f)
    }

    @Test
    fun `recordPageView updates on-demand loads when page was not prefetched`() {
        // Given: No prefetch
        val pages = createTestPages(5)

        // When: Record viewing a non-prefetched page
        prefetchManager.recordPageView(pages[0])

        // Then: Cache hit rate should be 0 (all on-demand)
        assertEquals(0f, prefetchManager.getCacheHitRate(), 0.01f)
    }

    @Test
    fun `resetTelemetry clears all counters`() = runTest(testDispatcher) {
        // Given: Some telemetry data
        val pages = createTestPages(5)
        prefetchManager.prefetchPages(
            pages = pages,
            currentPage = 0,
            strategy = PrefetchStrategy.Balanced,
            behavior = ReadingBehavior.DEFAULT,
            onlyOnWiFi = false,
            scope = CoroutineScope(testDispatcher)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        prefetchManager.recordPageView(pages[1])

        // When
        prefetchManager.resetTelemetry()

        // Then
        assertEquals(0f, prefetchManager.getCacheHitRate(), 0.01f)
        assertEquals(0f, prefetchManager.getPrefetchEfficiency(), 0.01f)
    }

    @Test
    fun `cancelPrefetch stops active job`() = runTest(testDispatcher) {
        // Given
        val pages = createTestPages(10)
        prefetchManager.prefetchPages(
            pages = pages,
            currentPage = 0,
            strategy = PrefetchStrategy.Balanced,
            behavior = ReadingBehavior.DEFAULT,
            onlyOnWiFi = false,
            scope = CoroutineScope(testDispatcher)
        )

        // When
        prefetchManager.cancelPrefetch()

        // Then: Job should be cancelled (no crash, cleanup happens)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `getTelemetryStats returns formatted string`() = runTest(testDispatcher) {
        // Given: Some telemetry data
        val pages = createTestPages(5)
        prefetchManager.prefetchPages(
            pages = pages,
            currentPage = 0,
            strategy = PrefetchStrategy.Conservative,
            behavior = ReadingBehavior.DEFAULT,
            onlyOnWiFi = false,
            scope = CoroutineScope(testDispatcher)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        val stats = prefetchManager.getTelemetryStats()

        // Then
        assertTrue(stats.contains("Smart Prefetch Telemetry"))
        assertTrue(stats.contains("Pages Prefetched"))
        assertTrue(stats.contains("Cache Hits"))
        assertTrue(stats.contains("Hit Rate"))
        assertTrue(stats.contains("Efficiency"))
        assertFalse(stats.contains("Pages Prefetched: 0"))
    }

    @Test
    fun `prefetchPages does not prefetch blank URLs`() = runTest(testDispatcher) {
        // Given: Pages with blank URLs
        val pages = listOf(
            ReaderPage(index = 0, imageUrl = null),
            ReaderPage(index = 1, imageUrl = ""),
            ReaderPage(index = 2, imageUrl = "   ")
        )

        // When
        prefetchManager.prefetchPages(
            pages = pages,
            currentPage = 0,
            strategy = PrefetchStrategy.Balanced,
            behavior = ReadingBehavior.DEFAULT,
            onlyOnWiFi = false,
            scope = CoroutineScope(testDispatcher)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: No prefetch should occur
        verify(exactly = 0) { imageLoader.enqueue(any()) }
    }

    // Helper function to create test pages
    private fun createTestPages(count: Int): List<ReaderPage> {
        return List(count) { index ->
            ReaderPage(
                index = index,
                imageUrl = "https://example.com/page$index.jpg",
                panels = emptyList()
            )
        }
    }
}
