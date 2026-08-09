package app.otakureader.domain.usecase

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.SourceChapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateLibraryMangaUseCaseTest {

    private lateinit var chapterRepository: ChapterRepository
    private lateinit var sourceRepository: SourceRepository
    private lateinit var useCase: UpdateLibraryMangaUseCase

    private val testManga = Manga(
        id = 1L,
        sourceId = 1L,
        url = "/m/1",
        title = "Naruto",
        favorite = true
    )

    private val existingChapter = Chapter(
        id = 1L,
        mangaId = 1L,
        url = "/c/1",
        name = "Chapter 1"
    )

    @Before
    fun setUp() {
        chapterRepository = mockk()
        sourceRepository = mockk()
        useCase = UpdateLibraryMangaUseCase(chapterRepository, sourceRepository)
    }

    @Test
    fun `invoke returns an empty list when no new chapters found`() = runTest {
        val sourceChapters = listOf(SourceChapter(url = "/c/1", name = "Chapter 1"))

        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(sourceChapters)
        every { chapterRepository.getChaptersByMangaId(1L) } returns flowOf(listOf(existingChapter))

        val result = useCase(testManga)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
    }

    @Test
    fun `invoke returns the new chapters, not just how many`() = runTest {
        val sourceChapters = listOf(
            SourceChapter(url = "/c/1", name = "Chapter 1"),
            SourceChapter(url = "/c/2", name = "Chapter 2"),
            SourceChapter(url = "/c/3", name = "Chapter 3")
        )

        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(sourceChapters)
        every { chapterRepository.getChaptersByMangaId(1L) } returns flowOf(listOf(existingChapter))
        coEvery { chapterRepository.insertChapters(any()) } returns Unit

        val result = useCase(testManga)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        coVerify(exactly = 1) { chapterRepository.insertChapters(any()) }
    }

    @Test
    fun `invoke returns failure when source fetch fails`() = runTest {
        val error = RuntimeException("Network error")
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.failure(error)
        every { chapterRepository.getChaptersByMangaId(1L) } returns flowOf(emptyList())

        val result = useCase(testManga)

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with all chapters already existing inserts nothing`() = runTest {
        val sourceChapters = listOf(SourceChapter(url = "/c/1", name = "Chapter 1"))
        val dbChapters = listOf(Chapter(id = 1L, mangaId = 1L, url = "/c/1", name = "Chapter 1"))

        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(sourceChapters)
        every { chapterRepository.getChaptersByMangaId(1L) } returns flowOf(dbChapters)

        val result = useCase(testManga)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
        coVerify(exactly = 0) { chapterRepository.insertChapters(any()) }
    }

    @Test
    fun `invoke with empty source result returns no new chapters`() = runTest {
        coEvery { sourceRepository.getChapterList(any(), any()) } returns Result.success(emptyList())
        every { chapterRepository.getChaptersByMangaId(1L) } returns flowOf(emptyList())

        val result = useCase(testManga)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
    }
}
