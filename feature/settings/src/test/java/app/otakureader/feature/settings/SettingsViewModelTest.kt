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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

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

        appearanceDelegate = mockk(relaxed = true)
        readerDelegate = mockk(relaxed = true)
        libraryDelegate = mockk(relaxed = true)
        downloadDelegate = mockk(relaxed = true)
        backupDelegate = mockk(relaxed = true)
        aiDelegate = mockk(relaxed = true)
        trackerSyncDelegate = mockk(relaxed = true)

        localSourcePreferences = mockk(relaxed = true) {
            every { localSourceDirectory } returns flowOf(null)
        }
        appPreferences = mockk(relaxed = true) {
            every { migrationSimilarityThreshold } returns flowOf(80)
            every { migrationAlwaysConfirm } returns flowOf(false)
            every { migrationMinChapterCount } returns flowOf(1)
        }
        readingGoalPreferences = mockk(relaxed = true) {
            every { dailyChapterGoal } returns flowOf(0)
            every { weeklyChapterGoal } returns flowOf(0)
            every { remindersEnabled } returns flowOf(false)
            every { reminderHour } returns flowOf(20)
        }
        readingReminderScheduler = mockk(relaxed = true)
        chapterRepository = mockk(relaxed = true)
        context = mockk(relaxed = true) {
            every { cacheDir } returns mockk(relaxed = true)
            every { getString(any()) } returns ""
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(
        appearanceDelegate = appearanceDelegate,
        readerDelegate = readerDelegate,
        libraryDelegate = libraryDelegate,
        downloadDelegate = downloadDelegate,
        backupDelegate = backupDelegate,
        aiDelegate = aiDelegate,
        trackerSyncDelegate = trackerSyncDelegate,
        localSourcePreferences = localSourcePreferences,
        appPreferences = appPreferences,
        readingGoalPreferences = readingGoalPreferences,
        readingReminderScheduler = readingReminderScheduler,
        chapterRepository = chapterRepository,
        context = context,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Initial state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial state has correct defaults`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.aiEnabled)
        assertFalse(state.showRemoveApiKeyDialog)
        assertEquals(0, state.dailyChapterGoal)
        assertEquals(0, state.weeklyChapterGoal)
        assertFalse(state.readingRemindersEnabled)
        assertEquals(20, state.readingReminderHour)
    }

    @Test
    fun `observeReadingGoalPreferences updates state from flows`() = runTest {
        every { readingGoalPreferences.dailyChapterGoal } returns flowOf(5)
        every { readingGoalPreferences.weeklyChapterGoal } returns flowOf(30)
        every { readingGoalPreferences.remindersEnabled } returns flowOf(true)
        every { readingGoalPreferences.reminderHour } returns flowOf(8)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(5, state.dailyChapterGoal)
        assertEquals(30, state.weeklyChapterGoal)
        assertEquals(true, state.readingRemindersEnabled)
        assertEquals(8, state.readingReminderHour)
    }

    @Test
    fun `observeMigrationPreferences updates state from flows`() = runTest {
        every { appPreferences.migrationSimilarityThreshold } returns flowOf(90)
        every { appPreferences.migrationAlwaysConfirm } returns flowOf(true)
        every { appPreferences.migrationMinChapterCount } returns flowOf(3)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(90, state.migrationSimilarityThreshold)
        assertEquals(true, state.migrationAlwaysConfirm)
        assertEquals(3, state.migrationMinChapterCount)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reading goal events
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SetDailyChapterGoal calls readingGoalPreferences`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetDailyChapterGoal(7))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { readingGoalPreferences.setDailyChapterGoal(7) }
    }

    @Test
    fun `SetWeeklyChapterGoal calls readingGoalPreferences`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetWeeklyChapterGoal(42))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { readingGoalPreferences.setWeeklyChapterGoal(42) }
    }

    @Test
    fun `SetReadingRemindersEnabled true schedules reminder`() = runTest {
        every { readingGoalPreferences.reminderHour } returns flowOf(9)
        coEvery { readingGoalPreferences.reminderHour.collect(any()) } returns Unit

        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetReadingRemindersEnabled(true))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { readingGoalPreferences.setRemindersEnabled(true) }
        coVerify { readingReminderScheduler.schedule(any()) }
    }

    @Test
    fun `SetReadingRemindersEnabled false cancels reminder`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetReadingRemindersEnabled(false))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { readingGoalPreferences.setRemindersEnabled(false) }
        coVerify { readingReminderScheduler.cancel() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Migration events
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SetMigrationSimilarityThreshold calls appPreferences`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetMigrationSimilarityThreshold(75))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appPreferences.setMigrationSimilarityThreshold(75) }
    }

    @Test
    fun `SetMigrationAlwaysConfirm calls appPreferences`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetMigrationAlwaysConfirm(true))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appPreferences.setMigrationAlwaysConfirm(true) }
    }

    @Test
    fun `SetMigrationMinChapterCount calls appPreferences`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetMigrationMinChapterCount(5))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appPreferences.setMigrationMinChapterCount(5) }
    }

    @Test
    fun `OnNavigateToMigration emits NavigateToMigrationEntry effect`() = runTest {
        val viewModel = createViewModel()

        viewModel.effect.test {
            viewModel.onEvent(SettingsEvent.OnNavigateToMigration)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(SettingsEffect.NavigateToMigrationEntry, awaitItem())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation events
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `NavigateToAbout emits NavigateToAbout effect`() = runTest {
        val viewModel = createViewModel()

        viewModel.effect.test {
            viewModel.onEvent(SettingsEvent.NavigateToAbout)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(SettingsEffect.NavigateToAbout, awaitItem())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data management events
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `ClearHistory calls chapterRepository clearAllHistory`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.ClearHistory)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { chapterRepository.clearAllHistory() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Local source events
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SetLocalSourceDirectory calls localSourcePreferences`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetLocalSourceDirectory("/storage/manga"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { localSourcePreferences.setLocalSourceDirectory("/storage/manga") }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delegate routing
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `onEvent routes AI events to aiDelegate`() = runTest {
        coEvery { aiDelegate.handleEvent(any(), any()) } returns true

        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetAiEnabled(true))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { aiDelegate.handleEvent(SettingsEvent.SetAiEnabled(true), any()) }
    }

    @Test
    fun `onEvent routes appearance events to appearanceDelegate`() = runTest {
        coEvery { appearanceDelegate.handleEvent(any(), any()) } returns true

        val viewModel = createViewModel()
        viewModel.onEvent(SettingsEvent.SetThemeMode(2))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appearanceDelegate.handleEvent(SettingsEvent.SetThemeMode(2), any()) }
    }
}
