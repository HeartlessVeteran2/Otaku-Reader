package app.otakureader.feature.browse

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
import kotlinx.coroutines.Dispatchers

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

    @Test
    fun `setSourceId loads popular manga and reads supportsLatest from the source`() = runTest {
        val source = createMangaSource(id = "1", supportsLatest = true)
        coEvery { sourceRepository.getSource("1") } returns source
        coEvery { sourceRepository.getPopularManga("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Manga 1", url = "url1", thumbnailUrl = null)), false))

        viewModel.setSourceId("1")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.supportsLatest)
        assertFalse(state.isShowingLatest)
        assertEquals(1, state.manga.size)
    }

    @Test
    fun `ToggleLatest switches to the latest listing`() = runTest {
        val source = createMangaSource(id = "1", supportsLatest = true)
        coEvery { sourceRepository.getSource("1") } returns source
        coEvery { sourceRepository.getPopularManga("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Popular", url = "popular", thumbnailUrl = null)), false))
        coEvery { sourceRepository.getLatestUpdates("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Latest", url = "latest", thumbnailUrl = null)), false))

        viewModel.setSourceId("1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(SourceMangaEvent.ToggleLatest)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isShowingLatest)
        assertEquals(1, state.manga.size)
        assertEquals("Latest", state.manga[0].title)
    }

    @Test
    fun `ToggleLatest twice returns to the popular listing`() = runTest {
        val source = createMangaSource(id = "1", supportsLatest = true)
        coEvery { sourceRepository.getSource("1") } returns source
        coEvery { sourceRepository.getPopularManga("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Popular", url = "popular", thumbnailUrl = null)), false))
        coEvery { sourceRepository.getLatestUpdates("1", 1) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Latest", url = "latest", thumbnailUrl = null)), false))

        viewModel.setSourceId("1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(SourceMangaEvent.ToggleLatest)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(SourceMangaEvent.ToggleLatest)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isShowingLatest)
        assertEquals("Popular", state.manga[0].title)
    }

    @Test
    fun `ToggleLatest is a no-op while in search mode`() = runTest {
        val source = createMangaSource(id = "1", supportsLatest = true)
        coEvery { sourceRepository.getSource("1") } returns source
        coEvery { sourceRepository.getPopularManga("1", 1) } returns
            Result.success(MangaPage(emptyList(), false))
        coEvery { sourceRepository.searchManga("1", "query", 1, any()) } returns
            Result.success(MangaPage(listOf(SourceManga(title = "Result", url = "result", thumbnailUrl = null)), false))

        viewModel.setSourceId("1", initialQuery = "query")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(SourceMangaEvent.ToggleLatest)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isShowingLatest)
        assertTrue(state.isSearchMode)
        assertEquals(1, state.manga.size)
    }
}
