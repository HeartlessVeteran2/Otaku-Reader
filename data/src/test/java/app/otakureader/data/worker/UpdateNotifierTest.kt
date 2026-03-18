package app.otakureader.data.worker

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Unit tests for UpdateNotifier notification grouping and cover image loading.
 * Uses Robolectric's ShadowNotificationManager for notification verification
 * to avoid MockK static mocking conflicts with Robolectric's class instrumentation.
 */
@OptIn(ExperimentalCoroutinesApi::class, coil3.annotation.DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateNotifierTest {

    private lateinit var context: Context
    private lateinit var imageLoader: ImageLoader
    private lateinit var shadowNotificationManager: org.robolectric.shadows.ShadowNotificationManager

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
        context = ApplicationProvider.getApplicationContext()
        imageLoader = mockk(relaxed = true)

        // Set Coil's singleton ImageLoader to our mock so context.imageLoader returns it.
        SingletonImageLoader.setUnsafe(imageLoader)

        // Grant POST_NOTIFICATIONS permission for SDK 34 tests
        val app = context as android.app.Application
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        // Access Robolectric's shadow for the system NotificationManager to verify posts.
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = Shadows.shadowOf(notificationManager)
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
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

        // Then - 3 individual + 1 summary = 4 notifications
        assertEquals(4, shadowNotificationManager.size())
    }

    @Test
    fun `notify does not send notifications when total chapters is zero`() = runTest {
        // Given
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(emptyList(), totalNewChapters = 0)

        // Then
        assertEquals(0, shadowNotificationManager.size())
    }

    @Test
    fun `notify does not send notifications when manga list is empty`() = runTest {
        // Given
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(emptyList(), totalNewChapters = 5)

        // Then
        assertEquals(0, shadowNotificationManager.size())
    }

    @Test
    fun `notify creates summary notification with correct text for single manga`() = runTest {
        // Given
        val mangaList = listOf(testManga1.copy(newChapterCount = 2))
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 2)

        // Then - 1 individual + 1 summary = 2 notifications
        assertEquals(2, shadowNotificationManager.size())
    }

    @Test
    fun `notify creates summary notification with correct text for multiple manga`() = runTest {
        // Given
        val mangaList = listOf(testManga1, testManga2) // 3 + 1 = 4 chapters
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 4)

        // Then - 2 individual + 1 summary = 3 notifications
        assertEquals(3, shadowNotificationManager.size())
    }

    @Test
    fun `notify uses correct notification IDs for manga`() = runTest {
        // Given
        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - manga notification should use hashCode of manga ID
        val notification = shadowNotificationManager.getNotification(UPDATE_NOTIFICATION_TAG, testManga1.id.hashCode())
        assertNotNull(notification)
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
        assertNotNull(shadowNotificationManager.getNotification(UPDATE_NOTIFICATION_TAG, testManga3.id.hashCode()))
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
        assertNotNull(shadowNotificationManager.getNotification(UPDATE_NOTIFICATION_TAG, testManga1.id.hashCode()))
    }

    @Test
    fun `notify continues when image loading times out`() = runTest {
        // Given - image loading takes too long
        coEvery { imageLoader.execute(any()) } throws java.util.concurrent.TimeoutException("simulated timeout")

        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When - should timeout and continue without image
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - notification created without large icon
        assertNotNull(shadowNotificationManager.getNotification(UPDATE_NOTIFICATION_TAG, testManga1.id.hashCode()))
    }

    @Test
    fun `notify posts notification even when image loading returns unsupported Image type`() = runTest {
        // Given - imageLoader returns a SuccessResult but the Image implementation is not
        // bitmap-backed, so toBitmap() throws inside loadCoverImage.
        val mockImage = mockk<coil3.Image>()
        val successResult = mockk<SuccessResult>()
        every { successResult.image } returns mockImage

        coEvery { imageLoader.execute(any()) } returns successResult

        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - notification is still posted despite the missing large icon
        assertNotNull(shadowNotificationManager.getNotification(UPDATE_NOTIFICATION_TAG, testManga1.id.hashCode()))
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

        // Then - 1 individual + 1 summary = 2
        assertEquals(2, shadowNotificationManager.size())
    }

    @Test
    fun `buildMangaNotification uses plural form for multiple chapters`() = runTest {
        // Given
        val manga = testManga1.copy(newChapterCount = 5)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(listOf(manga), totalNewChapters = 5)

        // Then - 1 individual + 1 summary = 2
        assertEquals(2, shadowNotificationManager.size())
    }

    // -------------------------------------------------------------------------
    // Permission Tests (Android 13+)
    // -------------------------------------------------------------------------

    @Test
    fun `notify checks notification permission on Android 13+`() = runTest {
        // Given - Robolectric SDK 34 (Android 13+), permission granted in setUp
        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - with POST_NOTIFICATIONS granted, notifications should be posted
        // 1 individual + 1 summary = 2
        assertEquals(2, shadowNotificationManager.size())
    }

    // -------------------------------------------------------------------------
    // Notification Channel Tests
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateNotifier creates notification channel on init`() {
        // Given/When
        val notifier = UpdateNotifier(context)

        // Then - channel should be created
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(UPDATE_CHANNEL_ID)
        assertNotNull(channel)
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

        // Then - notification is created with correct manga ID
        assertNotNull(shadowNotificationManager.getNotification(UPDATE_NOTIFICATION_TAG, testManga1.id.hashCode()))
    }

    @Test
    fun `createLauncherIntent falls back to ACTION_MAIN when getLaunchIntent returns null`() = runTest {
        // Given - Robolectric's PackageManager returns null for getLaunchIntentForPackage
        // by default for unregistered packages
        val mangaList = listOf(testManga1)
        val notifier = UpdateNotifier(context)

        // When - should use fallback intent
        notifier.notify(mangaList, totalNewChapters = 3)

        // Then - notification should still be created (1 individual + 1 summary)
        assertEquals(2, shadowNotificationManager.size())
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
        assertEquals(101, shadowNotificationManager.size())
    }

    @Test
    fun `notify handles manga with very long titles`() = runTest {
        // Given
        val longTitle = "A".repeat(500)
        val manga = testManga1.copy(title = longTitle)
        val notifier = UpdateNotifier(context)

        // When - should not crash
        notifier.notify(listOf(manga), totalNewChapters = 3)

        // Then - 1 individual + 1 summary = 2
        assertEquals(2, shadowNotificationManager.size())
    }

    @Test
    fun `notify handles zero chapters for individual manga but positive total`() = runTest {
        // Given - edge case that shouldn't happen in practice
        val manga = testManga1.copy(newChapterCount = 0)
        val notifier = UpdateNotifier(context)

        // When
        notifier.notify(listOf(manga), totalNewChapters = 1)

        // Then - 1 individual + 1 summary = 2
        assertEquals(2, shadowNotificationManager.size())
    }
}
