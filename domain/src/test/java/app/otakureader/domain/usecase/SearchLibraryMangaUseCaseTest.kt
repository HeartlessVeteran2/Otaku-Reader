package app.otakureader.domain.usecase

import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.MangaRepository
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchLibraryMangaUseCaseTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var useCase: SearchLibraryMangaUseCase

    private val sampleMangas = listOf(
        Manga(
            id = 1L, sourceId = 1L, url = "/m/1",
            title = "Naruto", author = "Masashi Kishimoto", artist = "Masashi Kishimoto",
            genre = listOf("Action", "Adventure", "Shounen"),
            status = MangaStatus.COMPLETED, favorite = true
        ),
        Manga(
            id = 2L, sourceId = 1L, url = "/m/2",
            title = "One Piece", author = "Eiichiro Oda", artist = "Eiichiro Oda",
            genre = listOf("Action", "Adventure", "Comedy", "Shounen"),
            status = MangaStatus.ONGOING, favorite = true
        ),
        Manga(
            id = 3L, sourceId = 2L, url = "/m/3",
            title = "Fruits Basket", author = "Natsuki Takaya",
            genre = listOf("Romance", "Drama", "Shoujo"),
            status = MangaStatus.COMPLETED, favorite = true
        ),
        Manga(
            id = 4L, sourceId = 2L, url = "/m/4",
            title = "Attack on Titan", author = "Hajime Isayama",
            genre = listOf("Action", "Drama", "Horror"),
            status = MangaStatus.COMPLETED, favorite = true
        )
    )

    @Before
    fun setUp() {
        mangaRepository = mockk()
        every { mangaRepository.getLibraryManga() } returns flowOf(sampleMangas)
        useCase = SearchLibraryMangaUseCase(mangaRepository)
    }

    @Test
    fun invoke_withBlankQuery_returnsAll() = runTest {
        useCase("").test {
            assertEquals(4, awaitItem().size)
            awaitComplete()
        }
    }

    @Test
    fun invoke_withSimpleTitleMatch_returnsMatchingManga() = runTest {
        useCase("naruto").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Naruto", result[0].title)
            awaitComplete()
        }
    }

    @Test
    fun invoke_withPartialTitleMatch_returnsMatchingManga() = runTest {
        useCase("piece").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("One Piece", result[0].title)
            awaitComplete()
        }
    }

    @Test
    fun invoke_withExcludeTerm_excludesMatchingManga() = runTest {
        useCase("-naruto").test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertTrue(result.none { it.title == "Naruto" })
            awaitComplete()
        }
    }

    @Test
    fun invoke_withTagFilter_returnsMatchingManga() = runTest {
        useCase("tag:romance").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Fruits Basket", result[0].title)
            awaitComplete()
        }
    }

    @Test
    fun invoke_withExcludeTag_excludesMatchingManga() = runTest {
        useCase("-tag:action").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Fruits Basket", result[0].title)
            awaitComplete()
        }
    }

    @Test
    fun invoke_withAuthorFilter_returnsMatchingManga() = runTest {
        useCase("author:Oda").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("One Piece", result[0].title)
            awaitComplete()
        }
    }

    @Test
    fun invoke_withStatusOngoing_returnsOnlyOngoing() = runTest {
        useCase("status:ongoing").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertTrue(result.all { it.status == MangaStatus.ONGOING })
            awaitComplete()
        }
    }

    @Test
    fun invoke_withStatusCompleted_returnsOnlyCompleted() = runTest {
        useCase("status:completed").test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertTrue(result.all { it.status == MangaStatus.COMPLETED })
            awaitComplete()
        }
    }

    @Test
    fun invoke_withExactPhrase_returnsMatchingManga() = runTest {
        useCase("\"Attack on Titan\"").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Attack on Titan", result[0].title)
            awaitComplete()
        }
    }

    @Test
    fun invoke_withNoMatch_returnsEmptyList() = runTest {
        useCase("zzznomatch").test {
            assertEquals(emptyList<Manga>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun invoke_withCombinedFilters_appliesAll() = runTest {
        // Action tag + completed status should give Naruto and Attack on Titan
        useCase("tag:action status:completed").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.status == MangaStatus.COMPLETED })
            assertTrue(result.all { it.genre.any { g -> g.lowercase() == "action" } })
            awaitComplete()
        }
    }

    @Test
    fun invoke_withCaseInsensitiveSearch_matchesRegardlessOfCase() = runTest {
        useCase("NARUTO").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Naruto", result[0].title)
            awaitComplete()
        }
    }
}
