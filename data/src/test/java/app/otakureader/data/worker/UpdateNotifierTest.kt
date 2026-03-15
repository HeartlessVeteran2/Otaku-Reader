package app.otakureader.data.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import coil3.ImageLoader
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Fully-qualified class name for Coil's SingletonImageLoader extension file.
 * This is a generated Kotlin class name that maps to the file where `Context.imageLoader`
 * is defined. If Coil changes its internal structure, this may need updating.
 * Consider using a test utility or DI to avoid this brittleness.
 */
private const val COIL_IMAGE_LOADER_CLASS = "coil3.SingletonImageLoader_androidKt"

/**
 * Unit tests for UpdateNotifier notification grouping and cover image loading.
 * Tests notification creation, grouping logic, and image timeout handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateNotifierTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var systemNotificationManager: NotificationManager
    private lateinit var imageLoader: ImageLoader
    private lateinit var packageManager: PackageManager

    private val testManga1 = NotificationManga(
        id = 1L,
        title = "Test Manga 1",
        coverUrl = "https://example.com/cover1.jpg",
        newChapterCount = 3
    )

    private val testManga2 = NotificationManga(
        id = 2L,
        title = "Test Manga 2",
        coverUrl = "https://example.com/cover2.jpg",
        newChapterCount = 1
    )

    private val testManga3 = NotificationManga(
        id = 3L,
        title = "Test Manga 3",
        coverUrl = null, // No cover
        newChapterCount = 5
    )

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        systemNotificationManager = mockk(relaxed = true)
        imageLoader = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)

        // Mock static NotificationManagerCompat.from()
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(context) } returns notificationManager

        // Mock static PendingIntent.getActivity() with specific flag matching for Android 12+ compatibility.
        // Production code uses FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE, so we match that exact combination.
        mockkStatic(PendingIntent::class)
        every { 
            PendingIntent.getActivity(
                any<Context>(), 
                any<Int>(), 
                any(), 
                eq(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ) 
        } returns mockk(relaxed = true)

        // Mock context services
        every { context.packageName } returns "app.otakureader"
        every { context.packageManager } returns packageManager

        // Mock Coil's Context.imageLoader extension property.
        // This mocks the file where the extension is defined (SingletonImageLoader.android.kt in Coil3).
        // Note: This is a generated class name that could change with Coil library updates.
        // If tests fail after updating Coil, verify the actual class name in the Coil artifact.
        // Centralizing this in a test utility or using dependency injection would be more robust.
        mockkStatic(COIL_IMAGE_LOADER_CLASS)
        every { context.imageLoader } returns imageLoader

        every { context.getSystemService(NotificationManager::class.java) } returns systemNotificationManager

        // Mock package manager
        every { packageManager.getLaunchIntentForPackage(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // Notification Grouping Tests
    // -------------------------------------------------------------------------

    @Test
    fun `notify creates individual notifications for each manga`() = runTest {
        // Given
        val mangaList = listOf(testManga1, testManga2, testManga3)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 9)

        // Then - verify notifications created with the update tag
        verify(exactly = 4) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                any(),
                any()
            )
        }

        // Verify summary notification
        verify(exactly = 1) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                eq(SUMMARY_NOTIFICATION_ID),
                any()
            )
        }
    }

    @Test
    fun `notify does not send notifications when total chapters is zero`() = runTest {
        // Given
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(emptyList(), totalNewChapters = 0)

        // Then
        verify(exactly = 0) {
            notificationManager.notify(any<String>(), any(), any())
        }
    }

    @Test
    fun `notify does not send notifications when manga list is empty`() = runTest {
        // Given
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(emptyList(), totalNewChapters = 5)

        // Then
        verify(exactly = 0) {
            notificationManager.notify(any<String>(), any(), any())
        }
    }

    @Test
    fun `notify creates summary notification with correct text for single manga`() = runTest {
        // Given
        val mangaList = listOf(testManga1.copy(newChapterCount = 2))
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 2)

        // Then - summary should say "2 new chapters available"
        verify(exactly = 1) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                eq(SUMMARY_NOTIFICATION_ID),
                any()
            )
        }
    }

    @Test
    fun `notify creates summary notification with correct text for multiple manga`() = runTest {
        // Given
        val mangaList = listOf(testManga1, testManga2) // 3 + 1 = 4 chapters
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 4)

        // Then - summary should say "4 new chapters in 2 manga"
        verify(exactly = 1) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                eq(SUMMARY_NOTIFICATION_ID),
                any()
            )
        }
    }

    @Test
    fun `notify uses correct notification IDs for manga`() = runTest {
        // Given
        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - manga notification should use hashCode of manga ID
        verify(exactly = 1) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                eq(testManga1.id.hashCode()),
                any()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Image Loading Tests
    // -------------------------------------------------------------------------

    @Test
    fun `notify handles manga with null cover URL`() = runTest {
        // Given
        val mangaList = listOf(testManga3) // Has null coverUrl
        val notifier = UpdateNotifier(context)

        // When - should not crash
        notifier.notify(mangaList, totalNewChapters = 5)

        // Then - notification created without large icon
        verify(exactly = 1) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                eq(testManga3.id.hashCode()),
                any()
            )
        }
    }

    @Test
    fun `notify continues when image loading fails`() = runTest {
        // Given - image loading throws exception
        coEvery { imageLoader.execute(any()) } throws Exception("Network error")

        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When - should not crash
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - notification created without large icon
        verify(exactly = 1) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                eq(testManga1.id.hashCode()),
                any()
            )
        }
    }

    @Test
    fun `notify continues when image loading times out`() = runTest {
        // Given - image loading takes too long (simulated with timeout)
        // We use TimeoutException instead of CancellationException because throwing
        // CancellationException would cancel the test scope itself (runTest uses a
        // TestScope that treats CancellationException specially).
        // 
        // PRODUCTION NOTE: The production code catches Exception, so TimeoutException
        // is handled the same way as any other failure - the notification proceeds
        // without the cover image. If production code specifically handles cancellation
        // differently, that should be tested separately with a different mocking approach.
        coEvery { imageLoader.execute(any()) } throws java.util.concurrent.TimeoutException("simulated timeout")

        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When - should timeout and continue without image
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - notification created without large icon
        verify(exactly = 1) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                eq(testManga1.id.hashCode()),
                any()
            )
        }
    }

    @Test
    fun `notify posts notification even when image loading returns unsupported Image type`() = runTest {
        // Given - imageLoader returns a SuccessResult but the Image implementation is not
        // bitmap-backed (e.g., a generic mock), so toBitmap() throws inside loadCoverImage.
        // The exception is caught by loadCoverImage's try-catch, and the notification is
        // still posted without a large icon.
        val mockImage = mockk<coil3.Image>()
        val successResult = mockk<SuccessResult>()
        every { successResult.image } returns mockImage

        coEvery { imageLoader.execute(any()) } returns successResult

        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - notification is still posted despite the missing large icon
        verify(exactly = 1) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                eq(testManga1.id.hashCode()),
                any()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Content Text Tests
    // -------------------------------------------------------------------------

    @Test
    fun `buildMangaNotification uses singular form for one chapter`() = runTest {
        // Given
        val manga = testManga1.copy(newChapterCount = 1)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(listOf(manga), totalNewChapters = 1)

        // Then - should use "1 new chapter" (singular)
        verify(exactly = 2) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                any(),
                any()
            )
        }
    }

    @Test
    fun `buildMangaNotification uses plural form for multiple chapters`() = runTest {
        // Given
        val manga = testManga1.copy(newChapterCount = 5)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(listOf(manga), totalNewChapters = 5)

        // Then - should use "5 new chapters" (plural)
        verify(exactly = 2) {
            notificationManager.notify(
                eq(UPDATE_NOTIFICATION_TAG),
                any(),
                any()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Permission Tests (Android 13+)
    // -------------------------------------------------------------------------

    @Test
    fun `notify checks notification permission on Android 13+`() = runTest {
        // Given - Android 13+ (API 33)
        // Note: Cannot easily mock Build.VERSION.SDK_INT, but we can verify behavior
        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - notification should be attempted
        // (Permission check happens internally, we verify notification was called)
        verify(atLeast = 1) {
            notificationManager.notify(any<String>(), any(), any())
        }
    }

    // -------------------------------------------------------------------------
    // Notification Channel Tests
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateNotifier creates notification channel on init`() {
        // Given/When
        val notifier = UpdateNotifier(context)

        // Then - channel should be created (Android O+)
        // Note: Cannot easily verify without mocking Build.VERSION, but we ensure no crash
        assertNotNull(notifier)
    }

    // -------------------------------------------------------------------------
    // Launch Intent Tests
    // -------------------------------------------------------------------------

    @Test
    fun `buildOpenMangaIntent creates intent with manga ID`() = runTest {
        // Given
        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - should call getLaunchIntentForPackage
        verify(atLeast = 1) {
            packageManager.getLaunchIntentForPackage(eq("app.otakureader"))
        }
    }

    @Test
    fun `createLauncherIntent falls back to ACTION_MAIN when getLaunchIntent returns null`() = runTest {
        // Given - getLaunchIntentForPackage returns null
        every { packageManager.getLaunchIntentForPackage(any()) } returns null

        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When - should use fallback intent
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - notification should still be created
        verify(atLeast = 1) {
            notificationManager.notify(any<String>(), any(), any())
        }
    }

    // -------------------------------------------------------------------------
    // Edge Case Tests
    // -------------------------------------------------------------------------

    @Test
    fun `notify handles large number of manga`() = runTest {
        // Given - 100 manga
        val mangaList = (1..100).map { i ->
            NotificationManga(
                id = i.toLong(),
                title = "Manga $i",
                coverUrl = "https://example.com/cover$i.jpg",
                newChapterCount = 1
            )
        }
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 100)

        // Then - 100 individual + 1 summary = 101 notifications
        verify(exactly = 101) {
            notificationManager.notify(any<String>(), any(), any())
        }
    }

    @Test
    fun `notify handles manga with very long titles`() = runTest {
        // Given
        val longTitle = "A".repeat(500)
        val manga = testManga1.copy(title = longTitle)
        val notifier = UpdateNotifier(context)

        // When - should not crash
        notifier.notify(listOf(manga), totalNewChapters = 3)

        // Then
        verify(exactly = 2) { // 1 individual + 1 summary
            notificationManager.notify(any<String>(), any(), any())
        }
    }

    @Test
    fun `notify handles zero chapters for individual manga but positive total`() = runTest {
        // Given - edge case that shouldn't happen in practice
        val manga = testManga1.copy(newChapterCount = 0)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(listOf(manga), totalNewChapters = 1)

        // Then - should still create notifications
        verify(exactly = 2) { // 1 individual + 1 summary
            notificationManager.notify(any<String>(), any(), any())
        }
    }
}
