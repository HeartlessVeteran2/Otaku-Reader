package app.otakureader.feature.browse

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.source.GetLatestUpdatesUseCase
import app.otakureader.domain.usecase.source.GetPopularMangaUseCase
import app.otakureader.domain.usecase.source.SearchMangaUseCase
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceManga
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Covers the Latest/Popular toggle added to bring SourceMangaScreen (the source browse
 * screen reached from Global Search) closer to parity with the main Sources-tab browse
 * flow, which already supports a Latest listing per source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceMangaViewModelTest {

    private val sourceRepository: SourceRepository = mockk()
    private val getPopularMangaUseCase = GetPopularMangaUseCase(sourceRepository)
    private val getLatestUpdatesUseCase = GetLatestUpdatesUseCase(sourceRepository)
    private val searchMangaUseCase = SearchMangaUseCase(sourceRepository)

    private lateinit var viewModel: SourceMangaViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SourceMangaViewModel(
            getPopularMangaUseCase = getPopularMangaUseCase,
            getLatestUpdatesUseCase = getLatestUpdatesUseCase,
            searchMangaUseCase = searchMangaUseCase,
            sourceRepository = sourceRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMangaSource(id: String, supportsLatest: Boolean = true) =
        mockk<MangaSource> {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns "Test Source"
            every { this@mockk.supportsLatest } returns supportsLatest
        }

    /** Awaits state emissions until [predicate] matches, bounded so a wrong stub can't hang the test. */
    private suspend fun ReceiveTurbine<SourceMangaState>.awaitUntil(
        predicate: (SourceMangaState) -> Boolean,
    ): SourceMangaState {
        var state = awaitItem()
        var attempts = 0
        while (!predicate(state) && attempts < MAX_AWAIT_ATTEMPTS) {
            state = awaitItem()
            attempts++
        }
        return state
    }

    @Test
    fun `setSourceId loads popular manga and reads supportsLatest from the source`() = runTest {
        val source = createMangaSource(id = "1", supportsLatest = true)
        coEvery { sourceRepository.getSource("1") } returns source
        coEvery { sourceRepository.getPopularManga("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Manga 1", url = "url1", thumbnailUrl = null)), false))

        viewModel.state.test {
            skipItems(1)
            viewModel.setSourceId("1")
            val state = awaitUntil { it.manga.isNotEmpty() }

            assertTrue(state.supportsLatest)
            assertFalse(state.isShowingLatest)
            assertEquals(1, state.manga.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleLatest switches to the latest listing`() = runTest {
        val source = createMangaSource(id = "1", supportsLatest = true)
        coEvery { sourceRepository.getSource("1") } returns source
        coEvery { sourceRepository.getPopularManga("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Popular", url = "popular", thumbnailUrl = null)), false))
        coEvery { sourceRepository.getLatestUpdates("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Latest", url = "latest", thumbnailUrl = null)), false))

        viewModel.state.test {
            skipItems(1)
            viewModel.setSourceId("1")
            awaitUntil { it.manga.isNotEmpty() }

            viewModel.onEvent(SourceMangaEvent.ToggleLatest)
            val state = awaitUntil { it.isShowingLatest && it.manga.isNotEmpty() }

            assertTrue(state.isShowingLatest)
            assertEquals(1, state.manga.size)
            assertEquals("Latest", state.manga[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleLatest twice returns to the popular listing`() = runTest {
        val source = createMangaSource(id = "1", supportsLatest = true)
        coEvery { sourceRepository.getSource("1") } returns source
        coEvery { sourceRepository.getPopularManga("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Popular", url = "popular", thumbnailUrl = null)), false))
        coEvery { sourceRepository.getLatestUpdates("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Latest", url = "latest", thumbnailUrl = null)), false))

        viewModel.state.test {
            skipItems(1)
            viewModel.setSourceId("1")
            awaitUntil { it.manga.isNotEmpty() }

            viewModel.onEvent(SourceMangaEvent.ToggleLatest)
            awaitUntil { it.isShowingLatest && it.manga.isNotEmpty() }

            viewModel.onEvent(SourceMangaEvent.ToggleLatest)
            val state = awaitUntil { !it.isShowingLatest && it.manga.isNotEmpty() }

            assertFalse(state.isShowingLatest)
            assertEquals("Popular", state.manga[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleLatest is a no-op while in search mode`() = runTest {
        val source = createMangaSource(id = "1", supportsLatest = true)
        coEvery { sourceRepository.getSource("1") } returns source
        coEvery { sourceRepository.getPopularManga("1", 1) } returns
            Result.success(MangaPage(emptyList(), false))
        coEvery { sourceRepository.searchManga("1", "query", 1, any()) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Result", url = "result", thumbnailUrl = null)), false))

        viewModel.state.test {
            skipItems(1)
            viewModel.setSourceId("1", initialQuery = "query")
            val loaded = awaitUntil { it.manga.isNotEmpty() }
            assertTrue(loaded.isSearchMode)

            viewModel.onEvent(SourceMangaEvent.ToggleLatest)

            // No new emission should follow from the no-op event; re-check state is unchanged
            // by asserting directly rather than awaiting (there is nothing further to await).
            val state = viewModel.state.value
            assertFalse(state.isShowingLatest)
            assertTrue(state.isSearchMode)
            assertEquals(1, state.manga.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val MAX_AWAIT_ATTEMPTS = 20
    }
}
