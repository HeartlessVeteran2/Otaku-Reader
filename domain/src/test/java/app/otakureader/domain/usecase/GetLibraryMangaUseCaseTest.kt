package app.otakureader.domain.usecase

import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.MangaRepository
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetLibraryMangaUseCaseTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var useCase: GetLibraryMangaUseCase

    private val sampleMangas = listOf(
        Manga(id = 1L, sourceId = 1L, url = "/m/1", title = "Naruto", favorite = true, status = MangaStatus.COMPLETED),
        Manga(id = 2L, sourceId = 1L, url = "/m/2", title = "One Piece", favorite = true, status = MangaStatus.ONGOING),
        Manga(id = 3L, sourceId = 2L, url = "/m/3", title = "Bleach", favorite = true, status = MangaStatus.COMPLETED)
    )

    @Before
    fun setUp() {
        mangaRepository = mockk()
        useCase = GetLibraryMangaUseCase(mangaRepository)
    }

    @Test
    fun invoke_delegatesToRepository() = runTest {
        every { mangaRepository.getLibraryManga() } returns flowOf(sampleMangas)

        useCase().test {
            assertEquals(sampleMangas, awaitItem())
            awaitComplete()
        }

        verify(exactly = 1) { mangaRepository.getLibraryManga() }
    }

    @Test
    fun invoke_withEmptyLibrary_emitsEmptyList() = runTest {
        every { mangaRepository.getLibraryManga() } returns flowOf(emptyList())

        useCase().test {
            assertEquals(emptyList<Manga>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun invoke_returnsAllLibraryManga() = runTest {
        every { mangaRepository.getLibraryManga() } returns flowOf(sampleMangas)

        useCase().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertTrue(result.all { it.favorite })
            awaitComplete()
        }
    }
}
