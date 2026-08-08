package app.otakureader.data.metadata

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListSearchRepositoryImplTest {

    private val api: AniListMetadataApi = mockk()
    private val repository = AniListSearchRepositoryImpl(api)

    private fun searchResponse(vararg media: SearchMedia) =
        SearchResponse(data = SearchData(page = SearchPage(media = media.toList())))

    @Test
    fun `maps every title form onto the candidate`() = runTest {
        coEvery { api.searchMedia("Berserk") } returns searchResponse(
            SearchMedia(
                id = 30002L,
                title = MetadataTitle(romaji = "Berserk", english = "Berserk", native = "ベルセルク"),
                synonyms = listOf("Berserk Deluxe Edition"),
            )
        )

        val candidate = repository.searchMedia("Berserk").getOrThrow().single()

        // All four forms matter — the matcher scores against every one of them, and dropping
        // `native` is what makes a Japanese-titled source fail to resolve.
        assertEquals(30002L, candidate.mediaId)
        assertEquals("Berserk", candidate.romaji)
        assertEquals("Berserk", candidate.english)
        assertEquals("ベルセルク", candidate.native)
        assertEquals(listOf("Berserk Deluxe Edition"), candidate.synonyms)
    }

    @Test
    fun `strips placeholder titles so they cannot become evidence`() = runTest {
        coEvery { api.searchMedia(any()) } returns searchResponse(
            SearchMedia(
                id = 1L,
                title = MetadataTitle(romaji = "Real Title", english = "?", native = "??"),
                synonyms = listOf("??", "Genuine Synonym"),
            )
        )

        val candidate = repository.searchMedia("Real Title").getOrThrow().single()

        // Two placeholders match each other perfectly, so leaving them in would let an entry whose
        // english title is "?" score 1.0 against a source whose title is also "?".
        assertEquals("Real Title", candidate.romaji)
        assertEquals(null, candidate.english)
        assertEquals(null, candidate.native)
        assertEquals(listOf("Genuine Synonym"), candidate.synonyms)
    }

    @Test
    fun `an entry with no usable romaji contributes an empty string rather than a placeholder`() = runTest {
        coEvery { api.searchMedia(any()) } returns searchResponse(
            SearchMedia(id = 1L, title = MetadataTitle(romaji = "?", english = "Real English"))
        )

        val candidate = repository.searchMedia("Real English").getOrThrow().single()

        assertEquals("", candidate.romaji)
        assertEquals("Real English", candidate.english)
    }

    @Test
    fun `a GraphQL error is a failure even though the transport succeeded`() = runTest {
        // AniList answers a rejected document with HTTP 200, so Retrofit throws nothing. Without
        // the errors-first check a refused search is indistinguishable from an empty one.
        coEvery { api.searchMedia(any()) } returns SearchResponse(
            data = null,
            errors = listOf(MetadataError("Invalid token")),
        )

        val result = repository.searchMedia("Berserk")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AniListMetadataException)
    }

    @Test
    fun `errors are checked before data, since GraphQL can return both`() = runTest {
        coEvery { api.searchMedia(any()) } returns SearchResponse(
            data = SearchData(page = SearchPage(media = listOf(SearchMedia(id = 1L)))),
            errors = listOf(MetadataError("partial resolver failure")),
        )

        assertTrue(repository.searchMedia("Berserk").isFailure)
    }

    @Test
    fun `no results is a success, not a failure`() = runTest {
        coEvery { api.searchMedia(any()) } returns searchResponse()

        val result = repository.searchMedia("Some Obscure Doujin")

        // A long-tail source title genuinely absent from AniList is a normal outcome; reporting it
        // as an error would make the caller abort a cascade that should continue.
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `a response with no page is a failure, not an empty search`() = runTest {
        // "AniList found nothing" and "AniList did not answer the question asked" must stay
        // distinguishable: the cascade advances on the first and aborts on the second, so calling
        // a malformed response empty would make four malformed requests where one already failed.
        coEvery { api.searchMedia(any()) } returns SearchResponse(data = SearchData(page = null))

        val result = repository.searchMedia("Berserk")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AniListMetadataException)
    }

    @Test
    fun `a response with no data at all is a failure`() = runTest {
        coEvery { api.searchMedia(any()) } returns SearchResponse(data = null)

        assertTrue(repository.searchMedia("Berserk").isFailure)
    }

    @Test
    fun `an empty media list is still a success`() = runTest {
        // The other side of the same line: `media: []` means AniList looked and found nothing,
        // which is a normal outcome the cascade must be allowed to move past.
        coEvery { api.searchMedia(any()) } returns
            SearchResponse(data = SearchData(page = SearchPage(media = emptyList())))

        val result = repository.searchMedia("Some Obscure Doujin")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `a placeholder query never reaches the network`() = runTest {
        val result = repository.searchMedia("??")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        coVerify(exactly = 0) { api.searchMedia(any()) }
    }

    @Test
    fun `a thrown transport error becomes a failure rather than escaping`() = runTest {
        coEvery { api.searchMedia(any()) } throws java.io.IOException("no route to host")

        val result = repository.searchMedia("Berserk")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
    }
}
