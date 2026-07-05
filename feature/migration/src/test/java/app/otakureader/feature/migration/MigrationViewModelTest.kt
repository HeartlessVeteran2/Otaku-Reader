package app.otakureader.feature.migration

import app.cash.turbine.test
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.model.MigrationFlag
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationResult
import app.otakureader.domain.model.MigrationStatus
import app.otakureader.sourceapi.MangaSource
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.migration.MigrateMangaUseCase
import app.otakureader.domain.usecase.migration.SearchMigrationTargetsUseCase
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
class MigrationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mangaRepository: MangaRepository
    private lateinit var sourceRepository: SourceRepository
    private lateinit var searchMigrationTargets: SearchMigrationTargetsUseCase
    private lateinit var migrateManga: MigrateMangaUseCase
    private lateinit var appPreferences: AppPreferences

    private val sampleManga = listOf(
        Manga(id = 1L, sourceId = 10L, url = "/m/1", title = "Naruto", favorite = true),
        Manga(id = 2L, sourceId = 10L, url = "/m/2", title = "Bleach", favorite = true),
    )
    private val sampleSources = listOf(
        mockk<MangaSource>(relaxed = true) {
            every { id } returns "1"
            every { name } returns "MangaDex"
            every { lang } returns "en"
        }
    )
    private val sampleCandidate = MigrationCandidate(
        sourceId = 2L, url = "/m/new/1", title = "Naruto",
        chapterCount = 100, similarityScore = 0.95f
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mangaRepository = mockk()
        sourceRepository = mockk {
            every { getSources() } returns flowOf(sampleSources)
        }
        searchMigrationTargets = mockk()
        migrateManga = mockk()
        appPreferences = mockk {
            every { migrationSimilarityThreshold } returns flowOf(0.8f)
            every { migrationAlwaysConfirm } returns flowOf(false)
            every { migrationMinChapterCount } returns flowOf(0)
            every { migrationFlags } returns flowOf(MigrationFlag.entries.toSet())
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MigrationViewModel(
        mangaRepository,
        sourceRepository,
        searchMigrationTargets,
        migrateManga,
        appPreferences
    )

    @Test
    fun init_stateIsEmpty() {
        val viewModel = createViewModel()
        assertTrue(viewModel.state.value.selectedManga.isEmpty())
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun onEvent_Initialize_loadsSelectedMangaAndSources() = runTest {
        coEvery { mangaRepository.getMangaByIds(listOf(1L, 2L)) } returns sampleManga

        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.Initialize(listOf(1L, 2L)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.selectedManga.size)
        assertEquals(1, viewModel.state.value.availableSources.size)
        assertEquals(2, viewModel.state.value.migrationTasks.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun onEvent_Initialize_withError_setsErrorState() = runTest {
        coEvery { mangaRepository.getMangaByIds(any()) } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.Initialize(listOf(1L)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun onEvent_SelectTargetSource_updatesSelectedSourceId() {
        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.SelectTargetSource(sourceId = 99L))
        assertEquals(99L, viewModel.state.value.selectedTargetSourceId)
    }

    @Test
    fun onEvent_SelectMigrationMode_updatesMigrationMode() {
        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.SelectMigrationMode(MigrationMode.COPY))
        assertEquals(MigrationMode.COPY, viewModel.state.value.migrationMode)
    }

    @Test
    fun onEvent_StartMigration_withNoTargetSource_setsError() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.StartMigration)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun onEvent_DismissError_clearsError() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.StartMigration) // triggers "no target source" error
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.onEvent(MigrationEvent.DismissError)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun onEvent_NavigateBack_emitsNavigateBackEffect() = runTest {
        val viewModel = createViewModel()

        viewModel.effect.test {
            viewModel.onEvent(MigrationEvent.NavigateBack)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is MigrationEffect.NavigateBack)
        }
    }

    @Test
    fun onEvent_SkipManga_marksTaskAsSkipped() = runTest {
        coEvery { mangaRepository.getMangaByIds(listOf(1L)) } returns listOf(sampleManga[0])

        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.Initialize(listOf(1L)))
        viewModel.onEvent(MigrationEvent.SelectTargetSource(sourceId = 2L))
        testDispatcher.scheduler.advanceUntilIdle()

        // Put the task in AWAITING_CONFIRMATION state first by setting up the dialog
        viewModel.onEvent(MigrationEvent.SkipManga(mangaId = 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        val task = viewModel.state.value.migrationTasks.find { it.manga.id == 1L }
        assertEquals(MigrationStatus.SKIPPED, task?.status)
    }

    @Test
    fun onEvent_DismissConfirmationDialog_hidesDialog() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.DismissConfirmationDialog)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showConfirmationDialog)
    }

    @Test
    fun onEvent_DismissCompletionSummary_hidesCompletionSummary() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.DismissCompletionSummary)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showCompletionSummary)
    }

    @Test
    fun onEvent_SearchForMatches_withNoTargetSource_setsError() = runTest {
        coEvery { mangaRepository.getMangaByIds(listOf(1L)) } returns listOf(sampleManga[0])

        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.Initialize(listOf(1L)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEvent.SearchForMatches(mangaId = 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun onEvent_SearchForMatches_withTargetSource_populatesCandidates() = runTest {
        coEvery { mangaRepository.getMangaByIds(listOf(1L)) } returns listOf(sampleManga[0])
        coEvery {
            searchMigrationTargets(sourceManga = sampleManga[0], targetSourceId = 2L)
        } returns Result.success(listOf(sampleCandidate))

        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.Initialize(listOf(1L)))
        viewModel.onEvent(MigrationEvent.SelectTargetSource(sourceId = 2L))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEvent.SearchForMatches(mangaId = 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.currentCandidates.size)
        assertTrue(viewModel.state.value.showConfirmationDialog)
    }

    @Test
    fun onEvent_Initialize_loadsMigrationFlagsFromPreferences() = runTest {
        coEvery { appPreferences.migrationFlags } returns flowOf(setOf(MigrationFlag.CHAPTERS, MigrationFlag.CATEGORIES))
        coEvery { mangaRepository.getMangaByIds(listOf(1L)) } returns listOf(sampleManga[0])

        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.Initialize(listOf(1L)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(MigrationFlag.CHAPTERS, MigrationFlag.CATEGORIES), viewModel.state.value.migrationFlags)
    }

    @Test
    fun onEvent_ToggleMigrationFlag_removesFlagAndPersists() = runTest {
        coEvery { appPreferences.setMigrationFlags(any()) } returns Unit
        coEvery { mangaRepository.getMangaByIds(listOf(1L)) } returns listOf(sampleManga[0])

        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.Initialize(listOf(1L)))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(MigrationFlag.TRACKING in viewModel.state.value.migrationFlags)

        viewModel.onEvent(MigrationEvent.ToggleMigrationFlag(MigrationFlag.TRACKING))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(MigrationFlag.TRACKING in viewModel.state.value.migrationFlags)
        coVerify { appPreferences.setMigrationFlags(match { MigrationFlag.TRACKING !in it }) }
    }

    @Test
    fun onEvent_ToggleMigrationFlag_reAddsFlagWhenToggledTwice() = runTest {
        coEvery { appPreferences.setMigrationFlags(any()) } returns Unit
        coEvery { mangaRepository.getMangaByIds(listOf(1L)) } returns listOf(sampleManga[0])

        val viewModel = createViewModel()
        viewModel.onEvent(MigrationEvent.Initialize(listOf(1L)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MigrationEvent.ToggleMigrationFlag(MigrationFlag.NOTES))
        viewModel.onEvent(MigrationEvent.ToggleMigrationFlag(MigrationFlag.NOTES))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(MigrationFlag.NOTES in viewModel.state.value.migrationFlags)
    }
}
