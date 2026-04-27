package app.otakureader.feature.settings

import android.content.Context
import app.cash.turbine.test
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.data.worker.ReadingReminderScheduler
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.feature.settings.delegate.AiSettingsDelegate
import app.otakureader.feature.settings.delegate.AppearanceSettingsDelegate
import app.otakureader.feature.settings.delegate.BackupSettingsDelegate
import app.otakureader.feature.settings.delegate.DownloadSettingsDelegate
import app.otakureader.feature.settings.delegate.LibrarySettingsDelegate
import app.otakureader.feature.settings.delegate.ReaderSettingsDelegate
import app.otakureader.feature.settings.delegate.TrackerSyncSettingsDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel] focused on the events that are handled directly
 * in [SettingsViewModel.handleRemainingEvent] (i.e. the ones not delegated to one of the
 * seven section delegates).
 *
 * The seven delegates are mocked with `relaxed = true` so [handleEvent] returns `false`
 * by default — that routes every event under test through `handleRemainingEvent`, which
 * is exactly the surface this test class exercises.
 *
 * Pattern matches `LibraryViewModelTest`: `StandardTestDispatcher` + `Dispatchers.setMain`,
 * Turbine for effect channel assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // Delegates — relaxed mocks; their `handleEvent` returns false by default so events
    // fall through to `handleRemainingEvent`.
    private lateinit var appearanceDelegate: AppearanceSettingsDelegate
    private lateinit var readerDelegate: ReaderSettingsDelegate
    private lateinit var libraryDelegate: LibrarySettingsDelegate
    private lateinit var downloadDelegate: DownloadSettingsDelegate
    private lateinit var backupDelegate: BackupSettingsDelegate
    private lateinit var aiDelegate: AiSettingsDelegate
    private lateinit var trackerSyncDelegate: TrackerSyncSettingsDelegate

    private lateinit var localSourcePreferences: LocalSourcePreferences
    private lateinit var appPreferences: AppPreferences
    private lateinit var readingGoalPreferences: ReadingGoalPreferences
    private lateinit var readingReminderScheduler: ReadingReminderScheduler
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // `SettingsState`'s default constructor calls `LocalSourcePreferences.defaultDirectory()`,
        // which in turn calls `Environment.getExternalStorageDirectory()` — not available in
        // plain JVM unit tests. Mock the companion so a literal is returned instead.
        mockkObject(LocalSourcePreferences.Companion)
        every { LocalSourcePreferences.defaultDirectory() } returns "/test/local"

        appearanceDelegate = mockk(relaxed = true)
        readerDelegate = mockk(relaxed = true)
        libraryDelegate = mockk(relaxed = true)
        downloadDelegate = mockk(relaxed = true)
        backupDelegate = mockk(relaxed = true)
        aiDelegate = mockk(relaxed = true)
        trackerSyncDelegate = mockk(relaxed = true)

        // Preferences flows return defaults so the observe-* coroutines in `init` complete
        // their first emission without exploding.
        localSourcePreferences = mockk {
            every { localSourceDirectory } returns flowOf("/test/local")
        }
        appPreferences = mockk(relaxed = true) {
            every { migrationSimilarityThreshold } returns flowOf(0.7f)
            every { migrationAlwaysConfirm } returns flowOf(false)
            every { migrationMinChapterCount } returns flowOf(0)
        }
        readingGoalPreferences = mockk(relaxed = true) {
            every { dailyChapterGoal } returns flowOf(0)
            every { weeklyChapterGoal } returns flowOf(0)
            every { remindersEnabled } returns flowOf(false)
            every { reminderHour } returns flowOf(20)
        }
        readingReminderScheduler = mockk(relaxed = true)
        chapterRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(LocalSourcePreferences.Companion)
    }

    private fun createViewModel(): SettingsViewModel = SettingsViewModel(
        appearanceDelegate,
        readerDelegate,
        libraryDelegate,
        downloadDelegate,
        backupDelegate,
        aiDelegate,
        trackerSyncDelegate,
        localSourcePreferences,
        appPreferences,
        readingGoalPreferences,
        readingReminderScheduler,
        chapterRepository,
        context,
    )

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial state matches SettingsState defaults`() = runTest {
        val viewModel = createViewModel()
        // Read the value before any preference flows have been collected; this is the
        // freshly-constructed state object.
        val initial = viewModel.state.value
        val expected = SettingsState()

        // Spot-check a representative cross-section of the defaults documented in
        // `SettingsState`. We don't assert the full data class equality here because the
        // observe-* coroutines may have already merged identical flow emissions in by the
        // time the ViewModel is constructed; comparing by field keeps the test robust.
        assertEquals(expected.themeMode, initial.themeMode)
        assertTrue(initial.useDynamicColor)
        assertFalse(initial.usePureBlackDarkMode)
        assertEquals(expected.readerMode, initial.readerMode)
        assertEquals(expected.libraryGridSize, initial.libraryGridSize)
        assertEquals(0, initial.dailyChapterGoal)
        assertEquals(0, initial.weeklyChapterGoal)
        assertEquals(20, initial.readingReminderHour)
        assertFalse(initial.readingRemindersEnabled)
        assertEquals(0.7f, initial.migrationSimilarityThreshold)
        assertFalse(initial.migrationAlwaysConfirm)
        assertEquals(0, initial.migrationMinChapterCount)
        assertEquals(SyncStatus.IDLE, initial.syncStatus)
    }

    // ── Reading goals ────────────────────────────────────────────────────────

    @Test
    fun `SetDailyChapterGoal forwards goal to ReadingGoalPreferences`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.SetDailyChapterGoal(goal = 5))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { readingGoalPreferences.setDailyChapterGoal(5) }
    }

    @Test
    fun `SetWeeklyChapterGoal forwards goal to ReadingGoalPreferences`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.SetWeeklyChapterGoal(goal = 35))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { readingGoalPreferences.setWeeklyChapterGoal(35) }
    }

    // ── Migration ────────────────────────────────────────────────────────────

    @Test
    fun `SetMigrationSimilarityThreshold forwards threshold to AppPreferences`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.SetMigrationSimilarityThreshold(threshold = 0.85f))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { appPreferences.setMigrationSimilarityThreshold(0.85f) }
    }

    @Test
    fun `OnNavigateToMigration emits NavigateToMigrationEntry effect`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(SettingsEvent.OnNavigateToMigration)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(SettingsEffect.NavigateToMigrationEntry, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    @Test
    fun `NavigateToAbout emits NavigateToAbout effect`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(SettingsEvent.NavigateToAbout)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(SettingsEffect.NavigateToAbout, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Data management ──────────────────────────────────────────────────────

    @Test
    fun `ClearHistory invokes chapterRepository clearAllHistory`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.ClearHistory)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chapterRepository.clearAllHistory() }
    }

    @Test
    fun `ClearHistory emits success snackbar effect`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(SettingsEvent.ClearHistory)
            testDispatcher.scheduler.advanceUntilIdle()

            // The exact string comes from a string resource that the relaxed Context mock
            // resolves to a non-null placeholder. We only need to confirm the effect is
            // a ShowSnackbar — i.e. that ClearHistory completed and surfaced user feedback.
            val effect = awaitItem()
            assertTrue(
                "Expected ShowSnackbar effect, got $effect",
                effect is SettingsEffect.ShowSnackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Routing sanity check ─────────────────────────────────────────────────

    @Test
    fun `event handled by a delegate is not routed to handleRemainingEvent`() = runTest {
        // Make the appearance delegate claim an event so we can verify the short-circuit:
        // when a delegate returns true, no further work happens for that event. We assert
        // this by checking that `appPreferences.setMigrationSimilarityThreshold` is NEVER
        // called when the appearance delegate handles the event.
        coEvery { appearanceDelegate.handleEvent(any(), any()) } returns true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.SetMigrationSimilarityThreshold(threshold = 0.5f))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { appPreferences.setMigrationSimilarityThreshold(any()) }
    }

    @Test
    fun `effect channel is not null and ready before any event`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.effect)
        assertNotNull(viewModel.state)
    }
}
