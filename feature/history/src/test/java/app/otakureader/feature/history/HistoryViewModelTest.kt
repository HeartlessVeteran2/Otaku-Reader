package app.otakureader.feature.history

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.ChapterWithHistory
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.usecase.GetHistoryUseCase
import app.cash.turbine.test
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getHistoryUseCase: GetHistoryUseCase
    private lateinit var chapterRepository: ChapterRepository

    private fun chapter(id: Long, name: String = "Chapter $id") =
        Chapter(id = id, mangaId = 1L, url = "/c/$id", name = name, read = true)

    private fun historyEntry(chapterId: Long, name: String = "Chapter $chapterId") =
        ChapterWithHistory(chapter = chapter(chapterId, name), readAt = chapterId * 1000L)

    private val sampleHistory = listOf(
        historyEntry(1L, "Chapter 1"),
        historyEntry(2L, "Chapter 2"),
        historyEntry(3L, "Dragon Slayer Arc")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getHistoryUseCase = mockk()
        chapterRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HistoryViewModel {
        return HistoryViewModel(getHistoryUseCase, chapterRepository)
    }

    @Test
    fun init_loadsHistoryOnCreation() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.history.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun init_withEmptyHistory_emitsEmptyList() = runTest {
        every { getHistoryUseCase() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<ChapterWithHistory>(), viewModel.state.value.history)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun onEvent_OnSearchQueryChange_filtersHistory() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.OnSearchQueryChange("Dragon"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.history.size)
        assertEquals("Dragon Slayer Arc", viewModel.state.value.history[0].chapter.name)
    }

    @Test
    fun onEvent_OnSearchQueryChange_withBlankQuery_showsAll() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.OnSearchQueryChange("Dragon"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.history.size)

        viewModel.onEvent(HistoryEvent.OnSearchQueryChange(""))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, viewModel.state.value.history.size)
    }

    @Test
    fun onEvent_OnSearchQueryChange_updatesSearchQueryState() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.OnSearchQueryChange("chapter"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("chapter", viewModel.state.value.searchQuery)
    }

    @Test
    fun onEvent_OnChapterLongClick_addsToSelection() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.OnChapterLongClick(chapterId = 1L))

        assertTrue(viewModel.state.value.selectedItems.contains(1L))
    }

    @Test
    fun onEvent_OnChapterLongClick_togglesSelection() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // First long click - adds
        viewModel.onEvent(HistoryEvent.OnChapterLongClick(chapterId = 2L))
        assertTrue(viewModel.state.value.selectedItems.contains(2L))

        // Second long click - removes
        viewModel.onEvent(HistoryEvent.OnChapterLongClick(chapterId = 2L))
        assertFalse(viewModel.state.value.selectedItems.contains(2L))
    }

    @Test
    fun onEvent_ClearSelection_removesAllSelectedItems() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.OnChapterLongClick(1L))
        viewModel.onEvent(HistoryEvent.OnChapterLongClick(2L))
        assertEquals(2, viewModel.state.value.selectedItems.size)

        viewModel.onEvent(HistoryEvent.ClearSelection)
        assertTrue(viewModel.state.value.selectedItems.isEmpty())
    }

    @Test
    fun onEvent_SelectAll_selectsAllHistoryItems() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.SelectAll)

        assertEquals(3, viewModel.state.value.selectedItems.size)
        assertTrue(viewModel.state.value.selectedItems.containsAll(listOf(1L, 2L, 3L)))
    }

    @Test
    fun onEvent_OnChapterClick_withNoSelection_emitsNavigateEffect() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(HistoryEvent.OnChapterClick(mangaId = 1L, chapterId = 1L))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is HistoryEffect.NavigateToReader)
            assertEquals(1L, (effect as HistoryEffect.NavigateToReader).mangaId)
            assertEquals(1L, effect.chapterId)
        }
    }

    @Test
    fun onEvent_OnChapterClick_withSelectionActive_togglesSelection() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Activate selection mode
        viewModel.onEvent(HistoryEvent.OnChapterLongClick(2L))
        assertTrue(viewModel.state.value.selectedItems.isNotEmpty())

        // Click in selection mode should toggle selection, not navigate
        viewModel.onEvent(HistoryEvent.OnChapterClick(mangaId = 1L, chapterId = 1L))
        assertTrue(viewModel.state.value.selectedItems.contains(1L))
    }

    @Test
    fun onEvent_ClearHistory_invokesRepositoryAndShowsSnackbar() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)
        coEvery { chapterRepository.clearAllHistory() } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(HistoryEvent.ClearHistory)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is HistoryEffect.ShowSnackbar)
            assertEquals("History cleared", (effect as HistoryEffect.ShowSnackbar).message)
        }

        coVerify(exactly = 1) { chapterRepository.clearAllHistory() }
    }

    @Test
    fun onEvent_ClearHistory_withError_showsErrorSnackbar() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)
        coEvery { chapterRepository.clearAllHistory() } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(HistoryEvent.ClearHistory)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is HistoryEffect.ShowSnackbar)
            assertEquals("Failed to clear history", (effect as HistoryEffect.ShowSnackbar).message)
        }
    }

    @Test
    fun onEvent_RemoveFromHistory_invokesRepository() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)
        coEvery { chapterRepository.removeFromHistory(any()) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.RemoveFromHistory(chapterId = 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chapterRepository.removeFromHistory(1L) }
    }

    @Test
    fun onEvent_RemoveSelectedFromHistory_removesAllSelectedAndClearsSelection() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)
        coEvery { chapterRepository.removeFromHistory(any()) } returns Unit

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.OnChapterLongClick(1L))
        viewModel.onEvent(HistoryEvent.OnChapterLongClick(2L))
        assertEquals(2, viewModel.state.value.selectedItems.size)

        viewModel.effect.test {
            viewModel.onEvent(HistoryEvent.RemoveSelectedFromHistory)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is HistoryEffect.ShowSnackbar)
            assertTrue((effect as HistoryEffect.ShowSnackbar).message.contains("2"))
        }

        assertTrue(viewModel.state.value.selectedItems.isEmpty())
        coVerify(exactly = 1) { chapterRepository.removeFromHistory(1L) }
        coVerify(exactly = 1) { chapterRepository.removeFromHistory(2L) }
    }

    @Test
    fun onEvent_RemoveSelectedFromHistory_withNoSelection_doesNothing() = runTest {
        every { getHistoryUseCase() } returns flowOf(sampleHistory)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // No items selected - should not call repository
        viewModel.onEvent(HistoryEvent.RemoveSelectedFromHistory)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { chapterRepository.removeFromHistory(any()) }
    }
}
