package app.otakureader.domain.usecase

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaUpdate
import app.otakureader.domain.repository.ChapterRepository
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetRecentUpdatesUseCaseTest {

    private lateinit var chapterRepository: ChapterRepository
    private lateinit var useCase: GetRecentUpdatesUseCase

    @Before
    fun setUp() {
        chapterRepository = mockk()
        useCase = GetRecentUpdatesUseCase(chapterRepository)
    }

    private fun testManga(id: Long) = Manga(
        id = id,
        sourceId = 1L,
        url = "/manga/$id",
        title = "Manga $id"
    )

    private fun testChapter(id: Long, mangaId: Long, dateFetch: Long = 1_700_000_000_000L) = Chapter(
        id = id,
        mangaId = mangaId,
        url = "/c/$id",
        name = "Chapter $id",
        dateFetch = dateFetch
    )

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val updates = listOf(MangaUpdate(manga = testManga(1L), chapter = testChapter(1L, 1L)))
        every { chapterRepository.getRecentUpdates() } returns flowOf(updates)

        useCase().test {
            assertEquals(updates, awaitItem())
            awaitComplete()
        }

        verify(exactly = 1) { chapterRepository.getRecentUpdates() }
    }

    @Test
    fun invoke_withNoUpdates_emitsEmptyList() = runTest {
        every { chapterRepository.getRecentUpdates() } returns flowOf(emptyList())

        useCase().test {
            assertEquals(emptyList<MangaUpdate>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun invoke_emitsMultipleUpdates() = runTest {
        val updates = (1..3).map { i ->
            MangaUpdate(manga = testManga(i.toLong()), chapter = testChapter(i.toLong(), i.toLong()))
        }
        every { chapterRepository.getRecentUpdates() } returns flowOf(updates)

        useCase().test {
            assertEquals(3, awaitItem().size)
            awaitComplete()
        }
    }

    @Test
    fun invoke_propagatesMultipleEmissions() = runTest {
        val first = listOf(MangaUpdate(manga = testManga(1L), chapter = testChapter(1L, 1L)))
        val second = first + MangaUpdate(manga = testManga(2L), chapter = testChapter(2L, 2L))

        every { chapterRepository.getRecentUpdates() } returns kotlinx.coroutines.flow.flow {
            emit(first)
            emit(second)
        }

        useCase().test {
            assertEquals(1, awaitItem().size)
            assertEquals(2, awaitItem().size)
            awaitComplete()
        }
    }
}
