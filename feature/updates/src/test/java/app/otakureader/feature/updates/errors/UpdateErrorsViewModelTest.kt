package app.otakureader.feature.updates.errors

import app.otakureader.domain.model.UpdateError
import app.otakureader.domain.repository.UpdateErrorRepository
import app.cash.turbine.test
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateErrorsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var updateErrorRepository: UpdateErrorRepository

    private val errorNaruto = UpdateError(
        mangaId = 1L,
        mangaTitle = "Naruto",
        thumbnailUrl = null,
        errorMessage = "HTTP 404",
        timestamp = 5_000L,
    )
    private val errorBleach = UpdateError(
        mangaId = 2L,
        mangaTitle = "Bleach",
        thumbnailUrl = null,
        errorMessage = "HTTP 404",
        timestamp = 6_000L,
    )
    private val errorOnePiece = UpdateError(
        mangaId = 3L,
        mangaTitle = "One Piece",
        thumbnailUrl = null,
        errorMessage = "Connection timed out",
        timestamp = 7_000L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        updateErrorRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UpdateErrorsViewModel =
        UpdateErrorsViewModel(updateErrorRepository)

    @Test
    fun init_groupsErrorsByMessage() = runTest {
        every { updateErrorRepository.observeErrors() } returns
            flowOf(listOf(errorNaruto, errorBleach, errorOnePiece))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.errorsByMessage.size)
        assertEquals(2, state.errorsByMessage.getValue("HTTP 404").size)
        assertEquals(1, state.errorsByMessage.getValue("Connection timed out").size)
    }

    @Test
    fun init_withNoErrors_isEmpty() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun toggleSelection_addsAndRemovesMangaId() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(1L))
        assertTrue(viewModel.state.value.isSelectionMode)
        assertTrue(1L in viewModel.state.value.selectedMangaIds)

        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(1L))
        assertFalse(viewModel.state.value.isSelectionMode)
    }

    @Test
    fun cardClick_whenNotInSelectionMode_emitsNavigateToManga() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(UpdateErrorsEvent.CardClick(1L))
            val effect = awaitItem()
            assertTrue(effect is UpdateErrorsEffect.NavigateToManga)
            assertEquals(1L, (effect as UpdateErrorsEffect.NavigateToManga).mangaId)
        }
    }

    @Test
    fun cardClick_whenInSelectionMode_togglesSelectionInstead() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto, errorBleach))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(1L))
        viewModel.onEvent(UpdateErrorsEvent.CardClick(2L))

        assertEquals(setOf(1L, 2L), viewModel.state.value.selectedMangaIds)
    }

    @Test
    fun selectAll_selectsEveryMangaId() = runTest {
        every { updateErrorRepository.observeErrors() } returns
            flowOf(listOf(errorNaruto, errorBleach, errorOnePiece))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.SelectAll)

        assertEquals(setOf(1L, 2L, 3L), viewModel.state.value.selectedMangaIds)
    }

    @Test
    fun invertSelection_flipsSelectedSet() = runTest {
        every { updateErrorRepository.observeErrors() } returns
            flowOf(listOf(errorNaruto, errorBleach, errorOnePiece))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(1L))
        viewModel.onEvent(UpdateErrorsEvent.InvertSelection)

        assertEquals(setOf(2L, 3L), viewModel.state.value.selectedMangaIds)
    }

    @Test
    fun clearSelection_emptiesSelectedSet() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(1L))
        viewModel.onEvent(UpdateErrorsEvent.ClearSelection)

        assertTrue(viewModel.state.value.selectedMangaIds.isEmpty())
    }

    @Test
    fun deleteError_delegatesToRepositoryForSingleManga() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.DeleteError(1L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { updateErrorRepository.clearError(1L) }
    }

    @Test
    fun deleteSelected_clearsEachSelectedMangaAndSelection() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto, errorBleach))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(1L))
        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(2L))
        viewModel.onEvent(UpdateErrorsEvent.DeleteSelected)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { updateErrorRepository.clearError(1L) }
        coVerify(exactly = 1) { updateErrorRepository.clearError(2L) }
        assertTrue(viewModel.state.value.selectedMangaIds.isEmpty())
    }

    @Test
    fun deleteSelected_withNoSelection_doesNothing() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.DeleteSelected)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateErrorRepository.clearError(any()) }
    }

    @Test
    fun clearAll_delegatesToRepositoryAndClearsSelection() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto, errorBleach))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(1L))
        viewModel.onEvent(UpdateErrorsEvent.ClearAll)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { updateErrorRepository.clearAllErrors() }
        assertTrue(viewModel.state.value.selectedMangaIds.isEmpty())
    }

    @Test
    fun migrateSelected_emitsNavigateToMigrationAndClearsSelection() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto, errorBleach))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(1L))
        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(2L))

        viewModel.effect.test {
            viewModel.onEvent(UpdateErrorsEvent.MigrateSelected)
            val effect = awaitItem()
            assertTrue(effect is UpdateErrorsEffect.NavigateToMigration)
            assertEquals(setOf(1L, 2L), (effect as UpdateErrorsEffect.NavigateToMigration).mangaIds.toSet())
        }
        assertTrue(viewModel.state.value.selectedMangaIds.isEmpty())
    }

    @Test
    fun migrateSelected_withNoSelection_doesNotEmitEffect() = runTest {
        every { updateErrorRepository.observeErrors() } returns flowOf(listOf(errorNaruto))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.MigrateSelected)
        testDispatcher.scheduler.advanceUntilIdle()
        // No effect should be pending; a subsequent CardClick's own effect proves the channel wasn't
        // already occupied by a stray MigrateSelected effect.
        viewModel.effect.test {
            viewModel.onEvent(UpdateErrorsEvent.CardClick(1L))
            val effect = awaitItem()
            assertTrue(effect is UpdateErrorsEffect.NavigateToManga)
        }
    }

    @Test
    fun selectionResolvedElsewhere_isDroppedFromSelection() = runTest {
        val errorsFlow = kotlinx.coroutines.flow.MutableStateFlow(listOf(errorNaruto, errorBleach))
        every { updateErrorRepository.observeErrors() } returns errorsFlow

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(1L))
        viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(2L))
        assertEquals(setOf(1L, 2L), viewModel.state.value.selectedMangaIds)

        // Manga 1's error resolves elsewhere (e.g. a background update succeeded).
        errorsFlow.value = listOf(errorBleach)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(2L), viewModel.state.value.selectedMangaIds)
    }
}
