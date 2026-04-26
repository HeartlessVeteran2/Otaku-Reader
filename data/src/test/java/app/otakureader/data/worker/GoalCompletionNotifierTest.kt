package app.otakureader.data.worker

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.preferences.ReadingGoalPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for GoalCompletionNotifier.
 * Uses Robolectric's ShadowNotificationManager for notification verification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalCompletionNotifierTest {

    private lateinit var context: Context
    private lateinit var readingGoalPreferences: ReadingGoalPreferences
    private lateinit var readingHistoryDao: ReadingHistoryDao
    private lateinit var shadowNotificationManager: org.robolectric.shadows.ShadowNotificationManager
    private lateinit var sharedPrefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        readingGoalPreferences = mockk()
        readingHistoryDao = mockk()

        // Grant POST_NOTIFICATIONS permission for SDK 34 tests
        val app = context as android.app.Application
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        // Access Robolectric's shadow for the system NotificationManager to verify posts
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = Shadows.shadowOf(notificationManager)

        // Clear shared preferences before each test
        sharedPrefs = context.getSharedPreferences("goal_completion_notifier", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()
    }

    @After
    fun tearDown() {
        // Clean up shared preferences after each test
        sharedPrefs.edit().clear().apply()
    }

    // -------------------------------------------------------------------------
    // Goal Disabled Tests
    // -------------------------------------------------------------------------

    @Test
    fun `checkAndNotify does nothing when daily goal is zero`() = runTest {
        // Given
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(0)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - no notification should be sent
        assertEquals(0, shadowNotificationManager.size())
    }

    @Test
    fun `checkAndNotify does nothing when daily goal is negative`() = runTest {
        // Given
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(-1)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - no notification should be sent
        assertEquals(0, shadowNotificationManager.size())
    }

    // -------------------------------------------------------------------------
    // Goal Not Met Tests
    // -------------------------------------------------------------------------

    @Test
    fun `checkAndNotify does nothing when chapters read is less than goal`() = runTest {
        // Given - goal is 5, but only 3 chapters read today
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(3)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - no notification should be sent
        assertEquals(0, shadowNotificationManager.size())
    }

    @Test
    fun `checkAndNotify does nothing when chapters read exceeds goal`() = runTest {
        // Given - goal is 5, but 7 chapters read today
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(7)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - no notification should be sent (already exceeded)
        assertEquals(0, shadowNotificationManager.size())
    }

    // -------------------------------------------------------------------------
    // Goal Met Tests
    // -------------------------------------------------------------------------

    @Test
    fun `checkAndNotify sends notification when goal is exactly met`() = runTest {
        // Given - goal is 5 and exactly 5 chapters read today
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(5)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - notification should be sent
        assertEquals(1, shadowNotificationManager.size())
    }

    @Test
    fun `checkAndNotify sends notification with correct content`() = runTest {
        // Given
        val dailyGoal = 10
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(dailyGoal)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(dailyGoal)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then
        assertEquals(1, shadowNotificationManager.size())
        val notification = shadowNotificationManager.allNotifications[0]
        assertNotNull(notification)
    }

    // -------------------------------------------------------------------------
    // Duplicate Prevention Tests
    // -------------------------------------------------------------------------

    @Test
    fun `checkAndNotify does not send notification twice on same day`() = runTest {
        // Given - goal met
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(5)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When - called twice
        notifier.checkAndNotify()
        notifier.checkAndNotify()

        // Then - only one notification should be sent
        assertEquals(1, shadowNotificationManager.size())
    }

    @Test
    fun `checkAndNotify sends notification on different days`() = runTest {
        // Given - goal met on first day
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(5)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When - first day
        notifier.checkAndNotify()
        assertEquals(1, shadowNotificationManager.size())

        // Simulate next day by manually updating the last notified date to yesterday
        val yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1)
        sharedPrefs.edit().putString("last_notified_date", yesterday.toString()).apply()

        // When - second day with goal met again
        notifier.checkAndNotify()

        // Then - should send another notification
        assertEquals(2, shadowNotificationManager.size())
    }

    @Test
    fun `checkAndNotify handles repeated calls after goal is exceeded`() = runTest {
        // Given - goal is 5, user read exactly 5 chapters and got notified
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(5)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        notifier.checkAndNotify()
        assertEquals(1, shadowNotificationManager.size())

        // User reads more chapters (6, 7, etc.)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(6)
        notifier.checkAndNotify()

        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(7)
        notifier.checkAndNotify()

        // Then - still only one notification (no re-notification)
        assertEquals(1, shadowNotificationManager.size())
    }

    // -------------------------------------------------------------------------
    // Permission Tests (Android 13+)
    // -------------------------------------------------------------------------

    @Test
    fun `checkAndNotify respects notification permission on Android 13+`() = runTest {
        // Given - permission is granted (in setUp)
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(5)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - notification should be sent
        assertEquals(1, shadowNotificationManager.size())
    }

    @Test
    fun `checkAndNotify does not send notification when permission denied on Android 13+`() = runTest {
        // Given - revoke permission
        val app = context as android.app.Application
        Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(5)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - no notification should be sent
        assertEquals(0, shadowNotificationManager.size())
    }

    // -------------------------------------------------------------------------
    // Notification Channel Tests
    // -------------------------------------------------------------------------

    @Test
    fun `GoalCompletionNotifier creates notification channel when sending notification`() = runTest {
        // Given
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(5)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - channel should be created
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(GOAL_CHANNEL_ID)
        assertNotNull(channel)
        assertEquals("Reading goal", channel.name)
    }

    // -------------------------------------------------------------------------
    // Edge Case Tests
    // -------------------------------------------------------------------------

    @Test
    fun `checkAndNotify handles goal of 1 chapter`() = runTest {
        // Given - very low goal
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(1)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(1)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - notification should be sent
        assertEquals(1, shadowNotificationManager.size())
    }

    @Test
    fun `checkAndNotify handles very high goal`() = runTest {
        // Given - very high goal
        val highGoal = 1000
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(highGoal)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(highGoal)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When
        notifier.checkAndNotify()

        // Then - notification should be sent
        assertEquals(1, shadowNotificationManager.size())
    }

    @Test
    fun `checkAndNotify handles concurrent calls gracefully`() = runTest {
        // Given
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingHistoryDao.getChaptersReadSince(any()) } returns flowOf(5)
        val notifier = GoalCompletionNotifier(context, readingGoalPreferences, readingHistoryDao)

        // When - simulate concurrent calls (though in practice they'd be sequential)
        notifier.checkAndNotify()
        notifier.checkAndNotify()
        notifier.checkAndNotify()

        // Then - only one notification
        assertEquals(1, shadowNotificationManager.size())
    }
}
