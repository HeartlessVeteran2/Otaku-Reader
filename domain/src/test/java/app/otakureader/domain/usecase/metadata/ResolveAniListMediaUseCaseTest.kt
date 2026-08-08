package app.otakureader.domain.usecase.metadata

import app.otakureader.domain.model.AniListMediaCandidate
import app.otakureader.domain.repository.AniListSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the half of matching that the matcher's own test cannot: which titles get searched, in what
 * order, and when the cascade stops.
 */
class ResolveAniListMediaUseCaseTest {

    private val searchRepository: AniListSearchRepository = mockk()
    private val resolve = ResolveAniListMediaUseCase(searchRepository, MatchAniListMediaUseCase())

    private fun candidate(
        id: Long,
        romaji: String = "",
        english: String? = null,
    ) = AniListMediaCandidate(id, romaji, english)

    @Test
    fun `searches the source title first and matches against its results`() = runTest {
        coEvery { searchRepository.searchMedia("Berserk") } returns
            Result.success(listOf(candidate(2L, romaji = "Berserk")))

        val match = resolve("Berserk").getOrThrow()

        assertEquals(2L, match?.candidate?.mediaId)
        assertTrue(match!!.confident)
        coVerify(exactly = 1) { searchRepository.searchMedia("Berserk") }
    }

    @Test
    fun `falls through to an alternative title when the first search finds nothing`() = runTest {
        // The case this exists for: AniList has no entry called "Boku no Hero Academia (Official
        // Colored)", so the source's own title returns an empty page and no scoring can rescue it.
        coEvery { searchRepository.searchMedia("Boku no Hero Academia (Official Colored)") } returns
            Result.success(emptyList())
        coEvery { searchRepository.searchMedia("My Hero Academia") } returns
            Result.success(listOf(candidate(31706L, romaji = "Boku no Hero Academia", english = "My Hero Academia")))

        val match = resolve(
            sourceTitle = "Boku no Hero Academia (Official Colored)",
            alternativeTitles = listOf("My Hero Academia"),
        ).getOrThrow()

        assertEquals(31706L, match?.candidate?.mediaId)
    }

    @Test
    fun `stops searching as soon as a search returns candidates`() = runTest {
        coEvery { searchRepository.searchMedia("Berserk") } returns
            Result.success(listOf(candidate(2L, romaji = "Berserk")))

        resolve(sourceTitle = "Berserk", alternativeTitles = listOf("Berserk Deluxe", "ベルセルク"))

        // Widening an already-populated candidate list is not worth a request against a rate limit
        // that can hold a response for 90 seconds.
        coVerify(exactly = 0) { searchRepository.searchMedia("Berserk Deluxe") }
        coVerify(exactly = 0) { searchRepository.searchMedia("ベルセルク") }
    }

    @Test
    fun `a failed search aborts the cascade instead of retrying the next title`() = runTest {
        coEvery { searchRepository.searchMedia("Berserk") } returns
            Result.failure(IllegalStateException("offline"))

        val result = resolve(sourceTitle = "Berserk", alternativeTitles = listOf("ベルセルク"))

        assertTrue(result.isFailure)
        // A dead request would be dead for the next title too; turning one into four is what the
        // rate-limit interceptor exists to prevent.
        coVerify(exactly = 0) { searchRepository.searchMedia("ベルセルク") }
    }

    @Test
    fun `every title is scored even when a later one produced the candidates`() = runTest {
        // "Kaguya-sama" finds nothing; the native title does. The candidates must still be judged
        // against the source title, or a search term that happens to be vague would decide the
        // match on its own.
        coEvery { searchRepository.searchMedia("Kaguya-sama wa Kokurasetai Season 2") } returns
            Result.success(emptyList())
        coEvery { searchRepository.searchMedia("かぐや様は告らせたい") } returns Result.success(
            listOf(
                candidate(1L, romaji = "Kaguya-sama wa Kokurasetai"),
                candidate(2L, romaji = "Kaguya-sama wa Kokurasetai Season 2"),
            )
        )

        val match = resolve(
            sourceTitle = "Kaguya-sama wa Kokurasetai Season 2",
            alternativeTitles = listOf("かぐや様は告らせたい"),
        ).getOrThrow()

        assertEquals(2L, match?.candidate?.mediaId)
    }

    @Test
    fun `returns null rather than a failure when no title finds anything`() = runTest {
        coEvery { searchRepository.searchMedia(any()) } returns Result.success(emptyList())

        val result = resolve(sourceTitle = "Some Obscure Doujin", alternativeTitles = listOf("Another Name"))

        // AniList genuinely having nothing is a normal outcome, not an error.
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `never searches more than MAX_SEARCH_ATTEMPTS titles`() = runTest {
        coEvery { searchRepository.searchMedia(any()) } returns Result.success(emptyList())

        resolve(
            sourceTitle = "T0",
            alternativeTitles = listOf("T1", "T2", "T3", "T4", "T5"),
        )

        coVerify(exactly = ResolveAniListMediaUseCase.MAX_SEARCH_ATTEMPTS) {
            searchRepository.searchMedia(any())
        }
        coVerify(exactly = 0) { searchRepository.searchMedia("T4") }
    }

    @Test
    fun `skips placeholder and duplicate titles without spending a request on them`() = runTest {
        coEvery { searchRepository.searchMedia(any()) } returns Result.success(emptyList())

        resolve(sourceTitle = "Berserk", alternativeTitles = listOf("?", "??", "  ", "berserk", "BERSERK"))

        // "?" and "??" are what sources emit for a missing localized title, and the three casings
        // of Berserk would all return the same page.
        coVerify(exactly = 1) { searchRepository.searchMedia(any()) }
    }

    @Test
    fun `returns null when the manga has no meaningful title at all`() = runTest {
        val result = resolve(sourceTitle = "?", alternativeTitles = listOf("  "))

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        coVerify(exactly = 0) { searchRepository.searchMedia(any()) }
    }
}
