package app.otakureader.domain.usecase.migration

import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.SourceManga
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchMigrationTargetsUseCaseTest {

    private lateinit var sourceRepository: SourceRepository
    private lateinit var useCase: SearchMigrationTargetsUseCase

    @Before
    fun setup() {
        sourceRepository = mockk()
        useCase = SearchMigrationTargetsUseCase(sourceRepository)
    }

    @Test
    fun `exact title match returns perfect score`() = runTest {
        // Given
        val sourceManga = createTestManga(title = "One Piece")
        val targetManga = createTestSourceManga(title = "One Piece")

        coEvery {
            sourceRepository.searchManga("1", "One Piece", 1)
        } returns Result.success(MangaPage(listOf(targetManga), false))

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isSuccess)
        val candidates = result.getOrNull()!!
        assertEquals(1, candidates.size)
        // Perfect title match with no author/genre gives 0.6 score (60% weight)
        assertTrue(candidates[0].similarityScore >= 0.6f)
    }

    @Test
    fun `year markers are ignored in matching`() = runTest {
        // Given
        val sourceManga = createTestManga(title = "Tokyo Ghoul (2011)")
        val targetManga = createTestSourceManga(title = "Tokyo Ghoul")

        coEvery {
            sourceRepository.searchManga("1", "Tokyo Ghoul (2011)", 1)
        } returns Result.success(MangaPage(listOf(targetManga), false))

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isSuccess)
        val candidates = result.getOrNull()!!
        assertEquals(1, candidates.size)
        // Perfect title match (after normalization) with no author/genre gives 0.6 score
        assertTrue(candidates[0].similarityScore >= 0.6f)
    }

    @Test
    fun `romanization variants get high score`() = runTest {
        // Given
        val sourceManga = createTestManga(title = "Boku no Hero Academia")
        val targetManga = createTestSourceManga(title = "My Hero Academia")

        coEvery {
            sourceRepository.searchManga("1", "Boku no Hero Academia", 1)
        } returns Result.success(MangaPage(listOf(targetManga), false))

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isSuccess)
        val candidates = result.getOrNull()!!
        assertEquals(1, candidates.size)
        // Should get a high score due to romanization bonus
        assertTrue(candidates[0].similarityScore >= 0.7f)
    }

    @Test
    fun `author matching boosts score`() = runTest {
        // Given
        val sourceManga = createTestManga(
            title = "One Piece",
            author = "Oda Eiichiro"
        )
        val targetManga1 = createTestSourceManga(
            title = "One Piece",
            author = "Oda Eiichiro"
        )
        val targetManga2 = createTestSourceManga(
            title = "One Piece",
            author = "Different Author"
        )

        coEvery {
            sourceRepository.searchManga("1", "One Piece", 1)
        } returns Result.success(MangaPage(listOf(targetManga1, targetManga2), false))

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isSuccess)
        val candidates = result.getOrNull()!!
        assertEquals(2, candidates.size)
        // First candidate (matching author) should score higher
        assertTrue(candidates[0].similarityScore > candidates[1].similarityScore)
        assertTrue(candidates[0].author == "Oda Eiichiro")
    }

    @Test
    fun `genre overlap boosts score`() = runTest {
        // Given
        val sourceManga = createTestManga(
            title = "One Piece",
            genre = listOf("Action", "Adventure", "Fantasy")
        )
        val targetManga1 = createTestSourceManga(
            title = "One Piece",
            genre = "Action, Adventure, Fantasy"
        )
        val targetManga2 = createTestSourceManga(
            title = "One Piece",
            genre = "Romance, Drama"
        )

        coEvery {
            sourceRepository.searchManga("1", "One Piece", 1)
        } returns Result.success(MangaPage(listOf(targetManga1, targetManga2), false))

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isSuccess)
        val candidates = result.getOrNull()!!
        assertEquals(2, candidates.size)
        // First candidate (matching genres) should score higher
        assertTrue(candidates[0].similarityScore > candidates[1].similarityScore)
    }

    @Test
    fun `common prefixes are removed for matching`() = runTest {
        // Given
        val sourceManga = createTestManga(title = "The Seven Deadly Sins")
        val targetManga = createTestSourceManga(title = "Seven Deadly Sins")

        coEvery {
            sourceRepository.searchManga("1", "The Seven Deadly Sins", 1)
        } returns Result.success(MangaPage(listOf(targetManga), false))

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isSuccess)
        val candidates = result.getOrNull()!!
        assertEquals(1, candidates.size)
        // With perfect title match (1.0) but no author/genre, score is 0.6 (60% weight)
        assertTrue("Expected score >= 0.6, got ${candidates[0].similarityScore}",
            candidates[0].similarityScore >= 0.6f)
    }

    @Test
    fun `multiple candidates are sorted by score`() = runTest {
        // Given
        val sourceManga = createTestManga(
            title = "One Piece",
            author = "Oda Eiichiro",
            genre = listOf("Action", "Adventure")
        )

        val perfectMatch = createTestSourceManga(
            title = "One Piece",
            author = "Oda Eiichiro",
            genre = "Action, Adventure"
        )
        val goodMatch = createTestSourceManga(
            title = "One Piece",
            author = "Different Author",
            genre = "Action"
        )
        val poorMatch = createTestSourceManga(
            title = "Two Piece",
            author = "Someone Else",
            genre = "Romance"
        )

        coEvery {
            sourceRepository.searchManga("1", "One Piece", 1)
        } returns Result.success(MangaPage(listOf(poorMatch, perfectMatch, goodMatch), false))

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isSuccess)
        val candidates = result.getOrNull()!!
        assertEquals(3, candidates.size)

        // Verify candidates are sorted by descending score
        assertTrue(candidates[0].similarityScore >= candidates[1].similarityScore)
        assertTrue(candidates[1].similarityScore >= candidates[2].similarityScore)

        // Perfect match should be first
        assertEquals("One Piece", candidates[0].title)
        assertEquals("Oda Eiichiro", candidates[0].author)
    }

    @Test
    fun `returns empty list when search returns no results`() = runTest {
        // Given
        val sourceManga = createTestManga(title = "Nonexistent Manga")

        coEvery {
            sourceRepository.searchManga("1", "Nonexistent Manga", 1)
        } returns Result.success(MangaPage(emptyList(), false))

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `returns failure when search fails`() = runTest {
        // Given
        val sourceManga = createTestManga(title = "Some Manga")
        val exception = Exception("Network error")

        coEvery {
            sourceRepository.searchManga("1", "Some Manga", 1)
        } returns Result.failure(exception)

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `special characters are handled correctly`() = runTest {
        // Given
        val sourceManga = createTestManga(title = "Hunter×Hunter")
        val targetManga = createTestSourceManga(title = "Hunter x Hunter")

        coEvery {
            sourceRepository.searchManga("1", "Hunter×Hunter", 1)
        } returns Result.success(MangaPage(listOf(targetManga), false))

        // When
        val result = useCase(sourceManga, 1L)

        // Then
        assertTrue(result.isSuccess)
        val candidates = result.getOrNull()!!
        assertEquals(1, candidates.size)
        // Both normalize to "hunter x hunter", perfect match with 0.6 score
        assertTrue(candidates[0].similarityScore >= 0.6f)
    }

    // Helper functions
    private fun createTestManga(
        id: Long = 1L,
        sourceId: Long = 1L,
        title: String,
        author: String? = null,
        genre: List<String> = emptyList()
    ) = Manga(
        id = id,
        sourceId = sourceId,
        url = "https://example.com/$id",
        title = title,
        author = author,
        genre = genre,
        status = MangaStatus.ONGOING
    )

    private fun createTestSourceManga(
        title: String,
        author: String? = null,
        genre: String? = null
    ) = SourceManga(
        url = "https://example.com/manga",
        title = title,
        author = author,
        genre = genre
    )
}
