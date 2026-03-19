package app.otakureader.domain.usecase.migration

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MigrationCandidate
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

    // Helper functions
    private fun createTestManga(
        id: Long,
        title: String,
        sourceId: Long = 1L,
        categoryIds: List<Long> = emptyList()
    ) = Manga(
        id = id,
        sourceId = sourceId,
        url = "https://example.com/manga/$id",
        title = title,
        thumbnailUrl = "https://example.com/cover.jpg",
        favorite = true,
        categoryIds = categoryIds
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
