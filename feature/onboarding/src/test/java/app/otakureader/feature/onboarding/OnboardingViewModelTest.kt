package app.otakureader.feature.onboarding

import app.cash.turbine.test
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var downloadPreferences: DownloadPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        generalPreferences = mockk()
        downloadPreferences = mockk()

        every { generalPreferences.themeMode } returns flowOf(0)
        // setThemeMode/setDownloadLocation both return DataStore.edit {}'s Preferences result,
        // not Unit — stub with a mock instance rather than the wrong return type.
        coEvery { generalPreferences.setThemeMode(any()) } returns mockk()
        every { downloadPreferences.downloadLocation } returns flowOf(null)
        coEvery { downloadPreferences.setDownloadLocation(any()) } returns mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = OnboardingViewModel(generalPreferences, downloadPreferences)

    @Test
    fun `downloadLocation defaults to null when nothing is picked yet`() = runTest {
        val viewModel = createViewModel()

        viewModel.downloadLocation.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `downloadLocation reflects the persisted value`() = runTest {
        every { downloadPreferences.downloadLocation } returns flowOf("content://tree/downloads")
        val viewModel = createViewModel()

        viewModel.downloadLocation.test {
            // stateIn(WhileSubscribed) only starts collecting the upstream once this test
            // subscribes, so the first emission is the seeded initial value (null); the real
            // value follows once the upstream collector coroutine gets to run.
            assertNull(awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("content://tree/downloads", awaitItem())
        }
    }

    @Test
    fun `setDownloadLocation writes through to preferences`() = runTest {
        val viewModel = createViewModel()

        viewModel.setDownloadLocation("content://tree/newpath")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { downloadPreferences.setDownloadLocation("content://tree/newpath") }
    }
}
