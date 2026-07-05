package app.otakureader.domain.usecase.migration

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.model.MigrationFlag
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationStatus
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MigrateMangaUseCaseTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var sourceRepository: SourceRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var trackRepository: TrackRepository
    private lateinit var useCase: MigrateMangaUseCase

    @Before
    fun setUp() {
        mangaRepository = mockk(relaxed = true)
        chapterRepository = mockk(relaxed = true)
        categoryRepository = mockk(relaxed = true)
        sourceRepository = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        trackRepository = mockk(relaxed = true)

        useCase = MigrateMangaUseCase(
            mangaRepository,
            chapterRepository,
            categoryRepository,
            sourceRepository,
            downloadRepository,
            trackRepository
        )
    }

    @Test
    fun `MOVE mode migrates tracker links to new manga and deletes old ones`() = runTest {
        // Given
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        // Create tracker entries for source manga
        val anilistEntry = TrackEntry(
            remoteId = 100L,
            mangaId = sourceMangaId,
            trackerId = TrackerType.ANILIST,
            title = "Test Manga",
            status = TrackStatus.READING,
            lastChapterRead = 10f
        )
        val malEntry = TrackEntry(
            remoteId = 200L,
            mangaId = sourceMangaId,
            trackerId = TrackerType.MY_ANIME_LIST,
            title = "Test Manga",
            status = TrackStatus.READING,
            lastChapterRead = 10f
        )
        val trackerEntries = listOf(anilistEntry, malEntry)

        // Mock setup
        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(trackerEntries)

        // When
        val result = useCase(sourceManga, targetCandidate, MigrationMode.MOVE)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MigrationStatus.COMPLETED, result.getOrNull()?.status)

        // Verify tracker entries were migrated to target manga
        val migratedEntrySlots = mutableListOf<TrackEntry>()
        coVerify(exactly = 2) { trackRepository.upsertEntry(capture(migratedEntrySlots)) }

        assertEquals(2, migratedEntrySlots.size)
        assertEquals(targetMangaId, migratedEntrySlots[0].mangaId)
        assertEquals(targetMangaId, migratedEntrySlots[1].mangaId)
        assertEquals(TrackerType.ANILIST, migratedEntrySlots[0].trackerId)
        assertEquals(TrackerType.MY_ANIME_LIST, migratedEntrySlots[1].trackerId)

        // Verify old tracker entries were NOT explicitly deleted in MOVE mode
        // (upsertEntry replaces by (trackerId, remoteId), so the old entries are replaced)
        coVerify(exactly = 0) { trackRepository.deleteEntry(any(), any()) }

        // Verify old manga was deleted
        coVerify(exactly = 1) { mangaRepository.deleteManga(sourceMangaId) }
    }

    @Test
    fun `COPY mode migrates tracker links to new manga and keeps old ones`() = runTest {
        // Given
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        val kitsuEntry = TrackEntry(
            remoteId = 300L,
            mangaId = sourceMangaId,
            trackerId = TrackerType.KITSU,
            title = "Test Manga",
            status = TrackStatus.COMPLETED,
            lastChapterRead = 50f,
            totalChapters = 50
        )
        val trackerEntries = listOf(kitsuEntry)

        // Mock setup
        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(trackerEntries)

        // When
        val result = useCase(sourceManga, targetCandidate, MigrationMode.COPY)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MigrationStatus.COMPLETED, result.getOrNull()?.status)

        // Verify tracker entry was migrated to target manga
        val migratedEntrySlot = slot<TrackEntry>()
        coVerify(exactly = 1) { trackRepository.upsertEntry(capture(migratedEntrySlot)) }

        assertEquals(targetMangaId, migratedEntrySlot.captured.mangaId)
        assertEquals(TrackerType.KITSU, migratedEntrySlot.captured.trackerId)
        assertEquals(300L, migratedEntrySlot.captured.remoteId)

        // Verify old tracker entries were NOT deleted in COPY mode
        coVerify(exactly = 0) { trackRepository.deleteEntry(any(), any()) }

        // Verify old manga was NOT deleted
        coVerify(exactly = 0) { mangaRepository.deleteManga(sourceMangaId) }
    }

    @Test
    fun `migrates multiple tracker services correctly`() = runTest {
        // Given
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Multi-Tracked Manga")
        val targetCandidate = createTestCandidate(title = "Multi-Tracked Manga (New)")

        // Create tracker entries for all supported trackers
        val allTrackerEntries = listOf(
            TrackEntry(
                remoteId = 1L,
                mangaId = sourceMangaId,
                trackerId = TrackerType.MY_ANIME_LIST,
                title = "Multi-Tracked Manga"
            ),
            TrackEntry(
                remoteId = 2L,
                mangaId = sourceMangaId,
                trackerId = TrackerType.ANILIST,
                title = "Multi-Tracked Manga"
            ),
            TrackEntry(
                remoteId = 3L,
                mangaId = sourceMangaId,
                trackerId = TrackerType.KITSU,
                title = "Multi-Tracked Manga"
            ),
            TrackEntry(
                remoteId = 4L,
                mangaId = sourceMangaId,
                trackerId = TrackerType.MANGA_UPDATES,
                title = "Multi-Tracked Manga"
            ),
            TrackEntry(
                remoteId = 5L,
                mangaId = sourceMangaId,
                trackerId = TrackerType.SHIKIMORI,
                title = "Multi-Tracked Manga"
            )
        )

        // Mock setup
        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(allTrackerEntries)

        // When
        val result = useCase(sourceManga, targetCandidate, MigrationMode.MOVE)

        // Then
        assertTrue(result.isSuccess)

        // Verify all 5 tracker entries were migrated
        val migratedEntrySlots = mutableListOf<TrackEntry>()
        coVerify(exactly = 5) { trackRepository.upsertEntry(capture(migratedEntrySlots)) }

        assertEquals(5, migratedEntrySlots.size)

        // Verify each tracker was migrated with correct data
        migratedEntrySlots.forEach { entry ->
            assertEquals(targetMangaId, entry.mangaId)
        }

        val trackerIds = migratedEntrySlots.map { it.trackerId }.toSet()
        assertTrue(trackerIds.contains(TrackerType.MY_ANIME_LIST))
        assertTrue(trackerIds.contains(TrackerType.ANILIST))
        assertTrue(trackerIds.contains(TrackerType.KITSU))
        assertTrue(trackerIds.contains(TrackerType.MANGA_UPDATES))
        assertTrue(trackerIds.contains(TrackerType.SHIKIMORI))

        // Verify old entries were NOT explicitly deleted in MOVE mode
        // (upsertEntry replaces by (trackerId, remoteId), so the old entries are replaced)
        coVerify(exactly = 0) { trackRepository.deleteEntry(any(), any()) }
    }

    @Test
    fun `migration without tracker links succeeds`() = runTest {
        // Given
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Untracked Manga")
        val targetCandidate = createTestCandidate(title = "Untracked Manga (New)")

        // Mock setup - no tracker entries
        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        // When
        val result = useCase(sourceManga, targetCandidate, MigrationMode.MOVE)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MigrationStatus.COMPLETED, result.getOrNull()?.status)

        // Verify no tracker operations were performed
        coVerify(exactly = 0) { trackRepository.upsertEntry(any()) }
        coVerify(exactly = 0) { trackRepository.deleteEntry(any(), any()) }
    }

    @Test
    fun `preserves tracker data during migration`() = runTest {
        // Given
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New)")

        val originalEntry = TrackEntry(
            remoteId = 999L,
            mangaId = sourceMangaId,
            trackerId = TrackerType.ANILIST,
            title = "Original Title",
            remoteUrl = "https://anilist.co/manga/999",
            status = TrackStatus.READING,
            lastChapterRead = 42f,
            totalChapters = 100,
            score = 9.5f,
            startDate = 1234567890L,
            finishDate = 0L
        )

        // Mock setup
        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(listOf(originalEntry))

        // When
        val result = useCase(sourceManga, targetCandidate, MigrationMode.COPY)

        // Then
        assertTrue(result.isSuccess)

        // Verify all tracker data was preserved except mangaId
        val migratedEntrySlot = slot<TrackEntry>()
        coVerify { trackRepository.upsertEntry(capture(migratedEntrySlot)) }

        val migratedEntry = migratedEntrySlot.captured
        assertEquals(targetMangaId, migratedEntry.mangaId) // Changed
        assertEquals(originalEntry.remoteId, migratedEntry.remoteId)
        assertEquals(originalEntry.trackerId, migratedEntry.trackerId)
        assertEquals(originalEntry.title, migratedEntry.title)
        assertEquals(originalEntry.remoteUrl, migratedEntry.remoteUrl)
        assertEquals(originalEntry.status, migratedEntry.status)
        assertEquals(originalEntry.lastChapterRead, migratedEntry.lastChapterRead)
        assertEquals(originalEntry.totalChapters, migratedEntry.totalChapters)
        assertEquals(originalEntry.score, migratedEntry.score)
        assertEquals(originalEntry.startDate, migratedEntry.startDate)
        assertEquals(originalEntry.finishDate, migratedEntry.finishDate)
    }

    @Test
    fun `migration succeeds when individual tracker upsert fails`() = runTest {
        // Given
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New)")

        val anilistEntry = TrackEntry(
            remoteId = 100L,
            mangaId = sourceMangaId,
            trackerId = TrackerType.ANILIST,
            title = "Test Manga",
            status = TrackStatus.READING,
            lastChapterRead = 10f
        )
        val malEntry = TrackEntry(
            remoteId = 200L,
            mangaId = sourceMangaId,
            trackerId = TrackerType.MY_ANIME_LIST,
            title = "Test Manga",
            status = TrackStatus.READING,
            lastChapterRead = 10f
        )
        val trackerEntries = listOf(anilistEntry, malEntry)

        // Mock setup - first upsert throws, second succeeds
        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(trackerEntries)
        coEvery {
            trackRepository.upsertEntry(match { it.trackerId == TrackerType.ANILIST })
        } throws RuntimeException("Network error")
        coEvery {
            trackRepository.upsertEntry(match { it.trackerId == TrackerType.MY_ANIME_LIST })
        } returns Unit

        // When
        val result = useCase(sourceManga, targetCandidate, MigrationMode.MOVE)

        // Then - migration should still succeed despite one tracker failure
        assertTrue(result.isSuccess)
        assertEquals(MigrationStatus.COMPLETED, result.getOrNull()?.status)

        // Both upsert attempts should have been made
        coVerify(exactly = 2) { trackRepository.upsertEntry(any()) }

        // Old manga should still be deleted (migration completes)
        coVerify(exactly = 1) { mangaRepository.deleteManga(sourceMangaId) }
    }

    @Test
    fun `migrates notes to a newly created target manga at insert time`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga", notes = "Great art style")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        val result = useCase(sourceManga, targetCandidate, MigrationMode.MOVE)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mangaRepository.insertManga(match { it.notes == "Great art style" }) }
        coVerify(exactly = 0) { mangaRepository.updateMangaNote(any(), any()) }
    }

    @Test
    fun `does not overwrite an existing target manga's notes`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga", notes = "Source notes")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")
        val existingTarget = createTestManga(id = targetMangaId, title = "Test Manga (New Source)", notes = "Already has notes")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns existingTarget
        // The notes check re-fetches the target's current state rather than reusing the
        // getMangaBySourceAndUrl snapshot (fixes a stale-read race — see MigrateMangaUseCase).
        coEvery { mangaRepository.getMangaById(targetMangaId) } returns existingTarget
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        val result = useCase(sourceManga, targetCandidate, MigrationMode.MOVE)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { mangaRepository.updateMangaNote(any(), any()) }
    }

    @Test
    fun `migrates notes to an existing target manga that has none of its own`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga", notes = "Source notes")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")
        val existingTarget = createTestManga(id = targetMangaId, title = "Test Manga (New Source)", notes = null)

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns existingTarget
        coEvery { mangaRepository.getMangaById(targetMangaId) } returns existingTarget
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        val result = useCase(sourceManga, targetCandidate, MigrationMode.MOVE)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mangaRepository.updateMangaNote(targetMangaId, "Source notes") }
    }

    @Test
    fun `re-fetches the existing target's notes instead of reusing the stale lookup snapshot`() = runTest {
        // The user adds a note to the target manga (e.g. from another screen) after the initial
        // getMangaBySourceAndUrl lookup but before the notes-migration step runs — simulating the
        // race the re-fetch fix guards against.
        val sourceMangaId = 1L
        val targetMangaId = 2L

        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga", notes = "Source notes")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")
        val staleTarget = createTestManga(id = targetMangaId, title = "Test Manga (New Source)", notes = null)
        val freshTarget = createTestManga(id = targetMangaId, title = "Test Manga (New Source)", notes = "Added mid-migration")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns staleTarget
        coEvery { mangaRepository.getMangaById(targetMangaId) } returns freshTarget
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        val result = useCase(sourceManga, targetCandidate, MigrationMode.MOVE)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { mangaRepository.updateMangaNote(any(), any()) }
    }

    // ── MigrationFlag gating tests (#1192 PR 6) ──────────────────────────

    @Test
    fun `CATEGORIES flag off skips category migration and cleanup`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L
        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga", categoryIds = listOf(10L, 20L))
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        val result = useCase(
            sourceManga, targetCandidate, MigrationMode.MOVE,
            flags = MigrationFlag.entries.toSet() - MigrationFlag.CATEGORIES
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { categoryRepository.addMangaToCategory(any(), any()) }
        coVerify(exactly = 0) { categoryRepository.removeMangaFromCategory(any(), any()) }
    }

    @Test
    fun `TRACKING flag off skips tracker migration entirely`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L
        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()

        val result = useCase(
            sourceManga, targetCandidate, MigrationMode.MOVE,
            flags = MigrationFlag.entries.toSet() - MigrationFlag.TRACKING
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { trackRepository.observeEntriesForManga(any()) }
        coVerify(exactly = 0) { trackRepository.upsertEntry(any()) }
    }

    @Test
    fun `NOTES flag off does not carry notes to a newly created target`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L
        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga", notes = "Great art style")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        val result = useCase(
            sourceManga, targetCandidate, MigrationMode.MOVE,
            flags = MigrationFlag.entries.toSet() - MigrationFlag.NOTES
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mangaRepository.insertManga(match { it.notes == null }) }
    }

    @Test
    fun `CUSTOM_COVER flag copies cover only when source has one`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L
        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga").copy(hasCustomCover = true)
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        val result = useCase(sourceManga, targetCandidate, MigrationMode.MOVE)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mangaRepository.copyCustomCover(sourceMangaId, targetMangaId) }
    }

    @Test
    fun `CUSTOM_COVER flag off skips cover copy even when source has one`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L
        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga").copy(hasCustomCover = true)
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns emptyList()
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        val result = useCase(
            sourceManga, targetCandidate, MigrationMode.MOVE,
            flags = MigrationFlag.entries.toSet() - MigrationFlag.CUSTOM_COVER
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { mangaRepository.copyCustomCover(any(), any()) }
    }

    @Test
    fun `DOWNLOADS flag off skips download migration for matched chapters`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L
        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")
        val sourceChapter = Chapter(
            id = 100L, mangaId = sourceMangaId, url = "/c/1", name = "Chapter 1",
            chapterNumber = 1f, read = true, lastPageRead = 5
        )
        val targetChapter = SourceChapter(url = "/new/c/1", name = "Chapter 1", chapterNumber = 1f)

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(listOf(targetChapter))
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns listOf(sourceChapter)
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())

        val result = useCase(
            sourceManga, targetCandidate, MigrationMode.MOVE,
            flags = MigrationFlag.entries.toSet() - MigrationFlag.DOWNLOADS
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.chaptersMatched) // matching still happens
        coVerify(exactly = 0) { downloadRepository.isChapterDownloaded(any(), any(), any()) }
        coVerify(exactly = 0) { downloadRepository.migrateChapterDownload(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `CHAPTERS flag off matches chapters but does not carry reading progress`() = runTest {
        val sourceMangaId = 1L
        val targetMangaId = 2L
        val sourceManga = createTestManga(id = sourceMangaId, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")
        val sourceChapter = Chapter(
            id = 100L, mangaId = sourceMangaId, url = "/c/1", name = "Chapter 1",
            chapterNumber = 1f, read = true, lastPageRead = 5
        )
        val targetChapter = SourceChapter(url = "/new/c/1", name = "Chapter 1", chapterNumber = 1f)

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns targetMangaId
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(listOf(targetChapter))
        coEvery { chapterRepository.getChaptersByMangaIdSync(sourceMangaId) } returns listOf(sourceChapter)
        coEvery { trackRepository.observeEntriesForManga(sourceMangaId) } returns flowOf(emptyList())
        coEvery { downloadRepository.isChapterDownloaded(any(), any(), any()) } returns false

        val insertedSlot = slot<List<Chapter>>()
        coEvery { chapterRepository.insertChapters(capture(insertedSlot)) } returns Unit

        val result = useCase(
            sourceManga, targetCandidate, MigrationMode.MOVE,
            flags = MigrationFlag.entries.toSet() - MigrationFlag.CHAPTERS
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.chaptersMatched) // matching still happens
        val insertedChapter = insertedSlot.captured.single()
        assertFalse(insertedChapter.read)
        assertEquals(0, insertedChapter.lastPageRead)
    }

    // Helper functions
    private fun createTestManga(
        id: Long,
        title: String,
        sourceId: Long = 1L,
        categoryIds: List<Long> = emptyList(),
        notes: String? = null
    ) = Manga(
        id = id,
        sourceId = sourceId,
        url = "https://example.com/manga/$id",
        title = title,
        thumbnailUrl = "https://example.com/cover.jpg",
        favorite = true,
        categoryIds = categoryIds,
        notes = notes
    )

    private fun createTestCandidate(
        title: String,
        sourceId: Long = 2L
    ) = MigrationCandidate(
        sourceId = sourceId,
        url = "https://newsource.com/manga",
        title = title,
        thumbnailUrl = "https://newsource.com/cover.jpg"
    )
}
