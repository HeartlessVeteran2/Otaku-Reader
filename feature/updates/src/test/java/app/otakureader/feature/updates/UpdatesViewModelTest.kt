package app.otakureader.feature.updates

import app.otakureader.core.preferences.GeneralPreferences
import androidx.datastore.preferences.core.Preferences
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.MangaUpdate
import app.otakureader.domain.usecase.GetRecentUpdatesUseCase
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getRecentUpdatesUseCase: GetRecentUpdatesUseCase
    private lateinit var generalPreferences: GeneralPreferences

    private val sampleManga = Manga(
        id = 1L, sourceId = 1L, url = "/m/1", title = "Naruto",
        status = MangaStatus.ONGOING, favorite = true
    )
    private val sampleChapter = Chapter(
        id = 10L, mangaId = 1L, url = "/c/10", name = "Chapter 100",
        chapterNumber = 100f, dateUpload = 1_000_000L
    )
    private val sampleUpdate = MangaUpdate(manga = sampleManga, chapter = sampleChapter)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getRecentUpdatesUseCase = mockk()
        generalPreferences = mockk {
            coEvery { setLastUpdatesViewedAt(any()) } returns mockk<Preferences>(relaxed = true)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UpdatesViewModel {
        return UpdatesViewModel(getRecentUpdatesUseCase, generalPreferences)
    }

    @Test
    fun init_loadsUpdatesOnCreation() = runTest {
        every { getRecentUpdatesUseCase() } returns flowOf(listOf(sampleUpdate))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.updates.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun init_setsLoadingTrueDuringFetch() = runTest {
        every { getRecentUpdatesUseCase() } returns flowOf(listOf(sampleUpdate))

        val viewModel = createViewModel()

        // Loading is true initially
        assertTrue(viewModel.state.value.isLoading)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun init_withEmptyUpdates_emitsEmptyList() = runTest {
        every { getRecentUpdatesUseCase() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.state.value.updates.size)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun init_withError_setsErrorState() = runTest {
        every { getRecentUpdatesUseCase() } returns flow { throw RuntimeException("Network error") }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertEquals("Network error", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun onEvent_Refresh_reloadsUpdates() = runTest {
        val firstList = listOf(sampleUpdate)
        val secondList = listOf(
            sampleUpdate,
            MangaUpdate(
                manga = sampleManga.copy(id = 2L, title = "Bleach"),
                chapter = sampleChapter.copy(id = 20L, mangaId = 2L, name = "Chapter 200")
            )
        )
        every { getRecentUpdatesUseCase() } returnsMany listOf(flowOf(firstList), flowOf(secondList))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.updates.size)

        viewModel.onEvent(UpdatesEvent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.updates.size)
    }

    @Test
    fun onEvent_OnChapterClick_emitsNavigateEffect() = runTest {
        every { getRecentUpdatesUseCase() } returns flowOf(listOf(sampleUpdate))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(UpdatesEvent.OnChapterClick(mangaId = 1L, chapterId = 10L))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is UpdatesEffect.NavigateToReader)
            assertEquals(1L, (effect as UpdatesEffect.NavigateToReader).mangaId)
            assertEquals(10L, effect.chapterId)
        }
    }

    @Test
    fun onEvent_OnChapterClick_passesCorrectIds() = runTest {
        every { getRecentUpdatesUseCase() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(UpdatesEvent.OnChapterClick(mangaId = 42L, chapterId = 99L))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem() as UpdatesEffect.NavigateToReader
            assertEquals(42L, effect.mangaId)
            assertEquals(99L, effect.chapterId)
        }
    }

    @Test
    fun init_marksUpdatesViewed() = runTest {
        every { getRecentUpdatesUseCase() } returns flowOf(emptyList())

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { generalPreferences.setLastUpdatesViewedAt(any()) }
    }
}
