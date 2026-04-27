package app.otakureader.data.worker

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

/**
 * API-level guard tests for UpdateNotifier.
 *
 * POST_NOTIFICATIONS (android.permission.POST_NOTIFICATIONS) was added in Android 13
 * (API 33 / TIRAMISU).  The [UpdateNotifier.notify] call must:
 * - On SDK < 33: skip the permission check entirely and always post notifications.
 * - On SDK >= 33: check the permission and silently return when it is denied.
 *
 * Robolectric's @Config(sdk = [...]) annotation controls the simulated SDK level.
 * Two companion test classes are used because @Config is class-level only.
 */
object UpdateNotifierApiGuardFixtures {
    val manga = NotificationManga(id = 1L, title = "Test Manga", coverUrl = null, newChapterCount = 2)
}

// ── SDK 28 (pre-TIRAMISU): no permission check required ─────────────────────

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateNotifierSdk28ApiGuardTest {

    private lateinit var context: Context
    private lateinit var shadowNm: ShadowNotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SingletonImageLoader.setUnsafe(mockk(relaxed = true))
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNm = Shadows.shadowOf(nm)
        // Deliberately do NOT grant POST_NOTIFICATIONS permission here
        // to verify the guard is not checked on pre-TIRAMISU devices.
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `on SDK 28 notifications are posted without POST_NOTIFICATIONS permission`() = runTest {
        val notifier = UpdateNotifier(context)
        notifier.notify(listOf(UpdateNotifierApiGuardFixtures.manga), totalNewChapters = 2)
        // 1 individual + 1 summary = 2 notifications, even without the permission
        assertEquals(2, shadowNm.size())
    }
}

// ── SDK 33 (TIRAMISU): POST_NOTIFICATIONS is enforced ───────────────────────

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateNotifierSdk33ApiGuardTest {

    private lateinit var context: Context
    private lateinit var shadowNm: ShadowNotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SingletonImageLoader.setUnsafe(mockk(relaxed = true))
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNm = Shadows.shadowOf(nm)
        // POST_NOTIFICATIONS is NOT granted — Robolectric denies unknown permissions by default.
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `on SDK 33 notifications are suppressed when POST_NOTIFICATIONS is denied`() = runTest {
        val notifier = UpdateNotifier(context)
        notifier.notify(listOf(UpdateNotifierApiGuardFixtures.manga), totalNewChapters = 2)
        // Guard should block the call before any notification is posted
        assertEquals(0, shadowNm.size())
    }

    @Test
    fun `on SDK 33 notifications are posted when POST_NOTIFICATIONS is granted`() = runTest {
        // Grant the permission at runtime
        val app = context as android.app.Application
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val notifier = UpdateNotifier(context)
        notifier.notify(listOf(UpdateNotifierApiGuardFixtures.manga), totalNewChapters = 2)
        // 1 individual + 1 summary = 2
        assertEquals(2, shadowNm.size())
    }
}
