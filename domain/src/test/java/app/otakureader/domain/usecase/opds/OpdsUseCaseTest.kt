package app.otakureader.domain.usecase.opds

import app.otakureader.domain.model.OpdsEntry
import app.otakureader.domain.model.OpdsFeed
import app.otakureader.domain.model.OpdsServer
import app.otakureader.domain.repository.OpdsRepository
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpdsUseCaseTest {

    private lateinit var repository: OpdsRepository

    private val testServer = OpdsServer(id = 1L, name = "Komga", url = "https://komga.example.com/opds/v1.2")
    private val testFeed = OpdsFeed(
        title = "Komga OPDS",
        entries = listOf(
            OpdsEntry(title = "Library A", id = "lib-a"),
            OpdsEntry(title = "Library B", id = "lib-b")
        ),
        links = emptyList()
    )

    @Before
    fun setUp() {
        repository = mockk()
    }

    // --- GetOpdsServersUseCase ---

    @Test
    fun `GetOpdsServers returns servers from repository`() = runTest {
        val servers = listOf(testServer, testServer.copy(id = 2L, name = "Kavita"))
        every { repository.getServers() } returns flowOf(servers)

        val useCase = GetOpdsServersUseCase(repository)
        useCase().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("Komga", result[0].name)
            assertEquals("Kavita", result[1].name)
            awaitComplete()
        }

        verify(exactly = 1) { repository.getServers() }
    }

    @Test
    fun `GetOpdsServers returns empty list when no servers`() = runTest {
        every { repository.getServers() } returns flowOf(emptyList())

        val useCase = GetOpdsServersUseCase(repository)
        useCase().test {
            assertEquals(emptyList<OpdsServer>(), awaitItem())
            awaitComplete()
        }
    }

    // --- SaveOpdsServerUseCase ---

    @Test
    fun `SaveOpdsServer delegates to repository`() = runTest {
        coEvery { repository.saveServer(testServer) } returns 1L

        val useCase = SaveOpdsServerUseCase(repository)
        val id = useCase(testServer)
        assertEquals(1L, id)

        coVerify(exactly = 1) { repository.saveServer(testServer) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SaveOpdsServer throws when name is blank`() = runTest {
        val useCase = SaveOpdsServerUseCase(repository)
        useCase(testServer.copy(name = ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SaveOpdsServer throws when name is whitespace`() = runTest {
        val useCase = SaveOpdsServerUseCase(repository)
        useCase(testServer.copy(name = "   "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SaveOpdsServer throws when url is blank`() = runTest {
        val useCase = SaveOpdsServerUseCase(repository)
        useCase(testServer.copy(url = ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SaveOpdsServer throws when url is whitespace`() = runTest {
        val useCase = SaveOpdsServerUseCase(repository)
        useCase(testServer.copy(url = "  "))
    }

    // --- DeleteOpdsServerUseCase ---

    @Test
    fun `DeleteOpdsServer delegates to repository`() = runTest {
        coEvery { repository.deleteServer(1L) } returns Unit

        val useCase = DeleteOpdsServerUseCase(repository)
        useCase(1L)

        coVerify(exactly = 1) { repository.deleteServer(1L) }
    }

    // --- BrowseOpdsCatalogUseCase ---

    @Test
    fun `BrowseOpdsCatalog delegates to repository`() = runTest {
        coEvery { repository.browseCatalog(testServer, testServer.url) } returns Result.success(testFeed)

        val useCase = BrowseOpdsCatalogUseCase(repository)
        val result = useCase(testServer, testServer.url)

        assertTrue(result.isSuccess)
        assertEquals("Komga OPDS", result.getOrNull()?.title)
        assertEquals(2, result.getOrNull()?.entries?.size)

        coVerify(exactly = 1) { repository.browseCatalog(testServer, testServer.url) }
    }

    @Test
    fun `BrowseOpdsCatalog propagates failure`() = runTest {
        val error = RuntimeException("Network error")
        coEvery { repository.browseCatalog(testServer, testServer.url) } returns Result.failure(error)

        val useCase = BrowseOpdsCatalogUseCase(repository)
        val result = useCase(testServer, testServer.url)

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    // --- SearchOpdsCatalogUseCase ---

    @Test
    fun `SearchOpdsCatalog delegates to repository`() = runTest {
        val searchUrl = "https://komga.example.com/opds/v1.2/search?q={searchTerms}"
        coEvery { repository.searchCatalog(testServer, searchUrl, "manga") } returns Result.success(testFeed)

        val useCase = SearchOpdsCatalogUseCase(repository)
        val result = useCase(testServer, searchUrl, "manga")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.searchCatalog(testServer, searchUrl, "manga") }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SearchOpdsCatalog throws when query is blank`() = runTest {
        val useCase = SearchOpdsCatalogUseCase(repository)
        useCase(testServer, "https://example.com/search", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SearchOpdsCatalog throws when query is whitespace`() = runTest {
        val useCase = SearchOpdsCatalogUseCase(repository)
        useCase(testServer, "https://example.com/search", "   ")
    }

    @Test
    fun `SearchOpdsCatalog propagates failure`() = runTest {
        val searchUrl = "https://komga.example.com/search"
        val error = RuntimeException("Timeout")
        coEvery { repository.searchCatalog(testServer, searchUrl, "test") } returns Result.failure(error)

        val useCase = SearchOpdsCatalogUseCase(repository)
        val result = useCase(testServer, searchUrl, "test")

        assertTrue(result.isFailure)
        assertEquals("Timeout", result.exceptionOrNull()?.message)
    }
}
