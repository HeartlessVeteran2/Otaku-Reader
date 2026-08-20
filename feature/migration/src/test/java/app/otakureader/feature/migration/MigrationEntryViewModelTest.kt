package app.otakureader.feature.migration

import app.cash.turbine.test
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.toSourceId
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
class MigrationEntryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var getLibraryManga: GetLibraryMangaUseCase
    private lateinit var sourceRepository: SourceRepository

    /** A source whose canonical key is the hash of its string id. */
    private fun source(id: String, name: String): MangaSource {
        val mock = mockk<MangaSource>(relaxed = true)
        every { mock.id } returns id
        every { mock.name } returns name
        return mock
    }

    private val sampleMangas = listOf(
        Manga(id = 1L, sourceId = 10L, url = "/m/1", title = "Naruto", favorite = true),
        Manga(id = 2L, sourceId = 10L, url = "/m/2", title = "Bleach", favorite = true),
        Manga(id = 3L, sourceId = 10L, url = "/m/3", title = "One Piece", favorite = true),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getLibraryManga = mockk()
        every { getLibraryManga.invoke() } returns flowOf(emptyList<Manga>())
        sourceRepository = mockk()
        every { sourceRepository.getSources() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MigrationEntryViewModel(getLibraryManga, sourceRepository)

    @Test
    fun init_loadsLibraryManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.mangaList.size)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun init_withEmptyLibrary_emitsEmptyList() = runTest {
        every { getLibraryManga() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.mangaList.isEmpty())
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun onEvent_OnMangaToggle_addsIdToSelection() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEntryEvent.OnMangaToggle(mangaId = 1L))

        assertTrue(viewModel.state.value.selectedIds.contains(1L))
    }

    @Test
    fun onEvent_OnMangaToggle_togglesSelection_whenAlreadySelected() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEntryEvent.OnMangaToggle(mangaId = 1L))
        assertTrue(viewModel.state.value.selectedIds.contains(1L))

        viewModel.onEvent(MigrationEntryEvent.OnMangaToggle(mangaId = 1L))
        assertFalse(viewModel.state.value.selectedIds.contains(1L))
    }

    @Test
    fun onEvent_SelectAll_selectsAllManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEntryEvent.SelectAll)

        assertEquals(3, viewModel.state.value.selectedIds.size)
        assertTrue(viewModel.state.value.selectedIds.containsAll(listOf(1L, 2L, 3L)))
    }

    @Test
    fun onEvent_ClearSelection_removesAllSelected() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEntryEvent.OnMangaToggle(mangaId = 1L))
        viewModel.onEvent(MigrationEntryEvent.OnMangaToggle(mangaId = 2L))
        assertEquals(2, viewModel.state.value.selectedIds.size)

        viewModel.onEvent(MigrationEntryEvent.ClearSelection)
        assertTrue(viewModel.state.value.selectedIds.isEmpty())
    }

    @Test
    fun onEvent_OnSearchQueryChange_updatesSearchQuery() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEntryEvent.OnSearchQueryChange("Naruto"))
        assertEquals("Naruto", viewModel.state.value.searchQuery)
    }

    @Test
    fun filteredList_withQuery_returnsMatchingManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEntryEvent.OnSearchQueryChange("Bleach"))

        val filtered = viewModel.filteredList()
        assertEquals(1, filtered.size)
        assertEquals("Bleach", filtered[0].title)
    }

    @Test
    fun filteredList_withBlankQuery_returnsAllManga() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEntryEvent.OnSearchQueryChange(""))
        assertEquals(3, viewModel.filteredList().size)
    }

    @Test
    fun onEvent_OnStartMigration_withSelection_emitsNavigateToMigrationEffect() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEntryEvent.OnMangaToggle(mangaId = 1L))
        viewModel.onEvent(MigrationEntryEvent.OnMangaToggle(mangaId = 2L))

        viewModel.effect.test {
            viewModel.onEvent(MigrationEntryEvent.OnStartMigration)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is MigrationEntryEffect.NavigateToMigration)
            val navigate = effect as MigrationEntryEffect.NavigateToMigration
            assertEquals(2, navigate.selectedMangaIds.size)
            assertTrue(navigate.selectedMangaIds.containsAll(listOf(1L, 2L)))
        }
    }

    @Test
    fun onEvent_OnStartMigration_withNoSelection_doesNotEmitEffect() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(MigrationEntryEvent.OnStartMigration)
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun onEvent_NavigateBack_emitsNavigateBackEffect() = runTest {
        every { getLibraryManga() } returns flowOf(sampleMangas)

        val viewModel = createViewModel()

        viewModel.effect.test {
            viewModel.onEvent(MigrationEntryEvent.NavigateBack)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is MigrationEntryEffect.NavigateBack)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Stranded entries — the recourse for a library row whose source no longer exists
    // ---------------------------------------------------------------------------------------

    @Test
    fun sourceName_resolvesThroughTheHashedKey() = runTest {
        val sourceId = "mangadex-en"
        val manga = Manga(id = 1L, sourceId = sourceId.toSourceId(), url = "/m/1", title = "Naruto", favorite = true)
        every { getLibraryManga() } returns flowOf(listOf(manga))
        every { sourceRepository.getSources() } returns flowOf(listOf(source(sourceId, "MangaDex")))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // The rule that bites: the row stores id.hashCode().toLong(), so anything that compares
        // sourceId.toString() against the source's id resolves nothing and marks a perfectly
        // healthy entry as stranded.
        val item = viewModel.state.value.mangaList.single()
        assertEquals("MangaDex", item.sourceName)
        assertFalse(item.isStranded)
        assertEquals(0, viewModel.state.value.strandedCount)
    }

    @Test
    fun sourceName_resolvesLegacyNumericKeys() = runTest {
        // Rows written by an older build stored the source's raw numeric id rather than the hash.
        // getSourceByKey still resolves those, so this screen has to as well — otherwise the same
        // source is reachable when reading a chapter and "not installed" here.
        val manga = Manga(id = 1L, sourceId = 10L, url = "/m/1", title = "Naruto", favorite = true)
        every { getLibraryManga() } returns flowOf(listOf(manga))
        every { sourceRepository.getSources() } returns flowOf(listOf(source("10", "Legacy Source")))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Legacy Source", viewModel.state.value.mangaList.single().sourceName)
    }

    @Test
    fun anEntryWithNoLoadedSourceIsStranded() = runTest {
        val manga = Manga(id = 1L, sourceId = 999L, url = "/m/1", title = "Orphan", favorite = true)
        every { getLibraryManga() } returns flowOf(listOf(manga))
        every { sourceRepository.getSources() } returns flowOf(listOf(source("other", "Other")))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val item = viewModel.state.value.mangaList.single()
        assertNull(item.sourceName)
        assertTrue(item.isStranded)
        assertEquals(1, viewModel.state.value.strandedCount)
    }

    @Test
    fun selectAllStranded_selectsOnlyTheBrokenEntries() = runTest {
        val healthyId = "alive"
        every { getLibraryManga() } returns flowOf(
            listOf(
                Manga(id = 1L, sourceId = healthyId.toSourceId(), url = "/m/1", title = "Healthy", favorite = true),
                Manga(id = 2L, sourceId = 424242L, url = "/m/2", title = "Broken", favorite = true),
            )
        )
        every { sourceRepository.getSources() } returns flowOf(listOf(source(healthyId, "Alive")))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(MigrationEntryEvent.SelectAllStranded)

        assertEquals(setOf(2L), viewModel.state.value.selectedIds)
    }

    @Test
    fun selectAllStranded_ignoresTheSearchQuery() = runTest {
        every { getLibraryManga() } returns flowOf(
            listOf(
                Manga(id = 1L, sourceId = 111L, url = "/m/1", title = "Naruto", favorite = true),
                Manga(id = 2L, sourceId = 222L, url = "/m/2", title = "Bleach", favorite = true),
            )
        )
        // Loaded, but owning neither key: both entries are genuinely stranded. An empty list would
        // mean "not loaded yet" instead, and nothing would be classified at all.
        every { sourceRepository.getSources() } returns flowOf(listOf(source("other", "Other")))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(MigrationEntryEvent.OnSearchQueryChange("Naruto"))
        viewModel.onEvent(MigrationEntryEvent.SelectAllStranded)

        // Both are stranded, and the action is "fix everything broken" — narrowing it by whatever
        // is typed in the search box would silently leave Bleach behind with no indication.
        assertEquals(setOf(1L, 2L), viewModel.state.value.selectedIds)
    }

    @Test
    fun strandedFilter_narrowsTheVisibleListAndRestoresIt() = runTest {
        val healthyId = "alive"
        every { getLibraryManga() } returns flowOf(
            listOf(
                Manga(id = 1L, sourceId = healthyId.toSourceId(), url = "/m/1", title = "Healthy", favorite = true),
                Manga(id = 2L, sourceId = 424242L, url = "/m/2", title = "Broken", favorite = true),
            )
        )
        every { sourceRepository.getSources() } returns flowOf(listOf(source(healthyId, "Alive")))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.filteredList().size)
        viewModel.onEvent(MigrationEntryEvent.ToggleStrandedFilter)
        assertEquals(listOf(2L), viewModel.filteredList().map { it.id })
        // Assert it toggles back too: a filter that only turns on strands the user in a view they
        // cannot leave.
        viewModel.onEvent(MigrationEntryEvent.ToggleStrandedFilter)
        assertEquals(2, viewModel.filteredList().size)
    }

    @Test
    fun nothingIsStrandedUntilSourcesLoad() = runTest {
        every { getLibraryManga() } returns flowOf(
            listOf(Manga(id = 1L, sourceId = 999L, url = "/m/1", title = "Orphan", favorite = true))
        )
        // The repository's source list is a StateFlow seeded empty; this is that first emission.
        every { sourceRepository.getSources() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // The row is published — withholding it hung the screen on its spinner forever — but no
        // verdict is reached, so the banner stays away and nothing invites migrating the library.
        assertEquals(1, viewModel.state.value.mangaList.size)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.sourcesKnown)
        assertEquals(0, viewModel.state.value.strandedCount)

        // And the action is inert rather than selecting everything.
        viewModel.onEvent(MigrationEntryEvent.SelectAllStranded)
        assertTrue(viewModel.state.value.selectedIds.isEmpty())
    }
}
