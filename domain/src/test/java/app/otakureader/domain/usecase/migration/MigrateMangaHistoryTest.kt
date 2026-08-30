package app.otakureader.domain.usecase.migration

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.model.MigrationFlag
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.ReadingHistoryEntry
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.sourceapi.SourceChapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Reading history under the [MigrationFlag.CHAPTERS] flag (#1208).
 *
 * Split out of `MigrateMangaUseCaseTest` rather than added to it: that class was already at
 * detekt's `LargeClass` limit, and these cases share none of its setup — every one of them turns
 * every flag *off* except `CHAPTERS`, because the point is what that one flag is responsible for.
 */
class MigrateMangaHistoryTest {

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

    /**
     * Reading history is the half of "chapter progress" that does not live on the chapter (#1208).
     *
     * `read` and `lastPageRead` are columns on `chapters` and were already carried over. When the
     * chapter was read, and for how long, lives in `reading_history` keyed by chapter id — a
     * different table the migration never touched, so a migrated series lost its History entry and
     * its contribution to the total reading time on the Statistics screen.
     *
     * The assertion is on the id rewrite specifically: the entry must arrive addressed to the
     * *target* chapter's id, with the source's timestamps intact.
     */
    @Test
    fun `CHAPTERS flag carries reading history onto the matched target chapter`() = runTest {
        val sourceManga = createTestManga(id = 1L, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")
        val sourceChapter = createTestChapter(id = 10L, mangaId = 1L, number = 1f, url = "/old/1")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns 2L
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(
            listOf(SourceChapter(url = "/new/1", name = "Chapter 1", chapterNumber = 1f))
        )
        coEvery { chapterRepository.getChaptersByMangaIdSync(1L) } returns listOf(sourceChapter)
        coEvery { chapterRepository.getHistoryForChapterIds(any()) } returns listOf(
            ReadingHistoryEntry(chapterId = 10L, readAt = 1_700L, readDurationMs = 90_000L)
        )
        // The ids Room assigned to the freshly-inserted target chapters, read back by url.
        coEvery { chapterRepository.getChaptersByMangaIdSync(2L) } returns listOf(
            createTestChapter(id = 77L, mangaId = 2L, number = 1f, url = "/new/1")
        )

        val result = useCase(
            sourceManga,
            targetCandidate,
            MigrationMode.COPY,
            flags = setOf(MigrationFlag.CHAPTERS)
        )

        assertTrue(result.isSuccess)
        val written = slot<List<ReadingHistoryEntry>>()
        coVerify { chapterRepository.replaceHistory(capture(written)) }
        assertEquals(
            "history must be re-addressed to the target chapter, values untouched",
            listOf(ReadingHistoryEntry(chapterId = 77L, readAt = 1_700L, readDurationMs = 90_000L)),
            written.captured
        )
    }

    /**
     * The flag has to actually gate this, or the migration options screen would be lying about what
     * it turns off. Chapter *matching* still runs without it, because downloads depend on it — so
     * "matched" is not evidence that progress was carried.
     */
    @Test
    fun `history is not carried when the CHAPTERS flag is off`() = runTest {
        val sourceManga = createTestManga(id = 1L, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns 2L
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(
            listOf(SourceChapter(url = "/new/1", name = "Chapter 1", chapterNumber = 1f))
        )
        coEvery { chapterRepository.getChaptersByMangaIdSync(1L) } returns listOf(
            createTestChapter(id = 10L, mangaId = 1L, number = 1f, url = "/old/1")
        )
        coEvery { chapterRepository.getHistoryForChapterIds(any()) } returns listOf(
            ReadingHistoryEntry(chapterId = 10L, readAt = 1_700L, readDurationMs = 90_000L)
        )

        val result = useCase(
            sourceManga,
            targetCandidate,
            MigrationMode.COPY,
            flags = setOf(MigrationFlag.DOWNLOADS)
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.chaptersMatched)
        coVerify(exactly = 0) { chapterRepository.replaceHistory(any()) }
    }

    /**
     * A chapter number that matched on the source side is no guarantee the target chapter's row can
     * be found afterwards — a source that renamed a url between the chapter-list fetch and the
     * read-back leaves nothing to address. Dropping that one entry is right; writing history against
     * a guessed id would attach someone's reading position to an unrelated chapter.
     */
    @Test
    fun `history for a target chapter that cannot be found again is dropped`() = runTest {
        val sourceManga = createTestManga(id = 1L, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns 2L
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(
            listOf(SourceChapter(url = "/new/1", name = "Chapter 1", chapterNumber = 1f))
        )
        coEvery { chapterRepository.getChaptersByMangaIdSync(1L) } returns listOf(
            createTestChapter(id = 10L, mangaId = 1L, number = 1f, url = "/old/1")
        )
        coEvery { chapterRepository.getHistoryForChapterIds(any()) } returns listOf(
            ReadingHistoryEntry(chapterId = 10L, readAt = 1_700L, readDurationMs = 90_000L)
        )
        coEvery { chapterRepository.getChaptersByMangaIdSync(2L) } returns listOf(
            createTestChapter(id = 77L, mangaId = 2L, number = 1f, url = "/somewhere/else")
        )

        val result = useCase(
            sourceManga,
            targetCandidate,
            MigrationMode.COPY,
            flags = setOf(MigrationFlag.CHAPTERS)
        )

        assertTrue(result.isSuccess)
        val written = slot<List<ReadingHistoryEntry>>()
        coVerify { chapterRepository.replaceHistory(capture(written)) }
        assertTrue("nothing addressable, so nothing written", written.captured.isEmpty())
    }

    /**
     * Migrating a series nobody has started must not cost a second query. The read-back only exists
     * to resolve ids for history that exists, so with no history there is nothing to resolve.
     */
    @Test
    fun `no source history means the target chapters are never read back`() = runTest {
        val sourceManga = createTestManga(id = 1L, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns 2L
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(
            listOf(SourceChapter(url = "/new/1", name = "Chapter 1", chapterNumber = 1f))
        )
        coEvery { chapterRepository.getChaptersByMangaIdSync(1L) } returns listOf(
            createTestChapter(id = 10L, mangaId = 1L, number = 1f, url = "/old/1")
        )
        coEvery { chapterRepository.getHistoryForChapterIds(any()) } returns emptyList()

        val result = useCase(
            sourceManga,
            targetCandidate,
            MigrationMode.COPY,
            flags = setOf(MigrationFlag.CHAPTERS)
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { chapterRepository.getChaptersByMangaIdSync(2L) }
        coVerify(exactly = 0) { chapterRepository.replaceHistory(any()) }
    }

    /**
     * Two target chapters with the same number — a second scanlation, a re-upload — both match the
     * same source chapter. The reading time was spent once, so it may only be written once.
     *
     * Getting this wrong inflates the Statistics screen's totals by a chapter's worth of reading
     * nobody did, which is the same double-counting `replaceHistory` exists to prevent on a re-run,
     * arriving by a different route. The read flag legitimately lands on both — they are the same
     * chapter — but a duration is a measurement.
     */
    @Test
    fun `a source chapter matching two target chapters has its history written once`() = runTest {
        val sourceManga = createTestManga(id = 1L, title = "Test Manga")
        val targetCandidate = createTestCandidate(title = "Test Manga (New Source)")

        coEvery { mangaRepository.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaRepository.insertManga(any()) } returns 2L
        coEvery { sourceRepository.getMangaDetails(any(), any()) } returns Result.success(mockk())
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(
            listOf(
                SourceChapter(url = "/new/1-groupA", name = "Chapter 1", chapterNumber = 1f),
                SourceChapter(url = "/new/1-groupB", name = "Chapter 1", chapterNumber = 1f),
            )
        )
        coEvery { chapterRepository.getChaptersByMangaIdSync(1L) } returns listOf(
            createTestChapter(id = 10L, mangaId = 1L, number = 1f, url = "/old/1")
        )
        coEvery { chapterRepository.getHistoryForChapterIds(any()) } returns listOf(
            ReadingHistoryEntry(chapterId = 10L, readAt = 1_700L, readDurationMs = 90_000L)
        )
        coEvery { chapterRepository.getChaptersByMangaIdSync(2L) } returns listOf(
            createTestChapter(id = 77L, mangaId = 2L, number = 1f, url = "/new/1-groupA"),
            createTestChapter(id = 78L, mangaId = 2L, number = 1f, url = "/new/1-groupB"),
        )

        val result = useCase(
            sourceManga,
            targetCandidate,
            MigrationMode.COPY,
            flags = setOf(MigrationFlag.CHAPTERS)
        )

        assertTrue(result.isSuccess)
        val written = slot<List<ReadingHistoryEntry>>()
        coVerify { chapterRepository.replaceHistory(capture(written)) }
        assertEquals(
            "90 seconds of reading must not become 180",
            listOf(ReadingHistoryEntry(chapterId = 77L, readAt = 1_700L, readDurationMs = 90_000L)),
            written.captured
        )
    }

    private fun createTestChapter(
        id: Long,
        mangaId: Long,
        number: Float,
        url: String
    ) = Chapter(
        id = id,
        mangaId = mangaId,
        url = url,
        name = "Chapter $number",
        chapterNumber = number
    )

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
