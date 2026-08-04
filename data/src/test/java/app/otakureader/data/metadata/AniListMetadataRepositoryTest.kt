package app.otakureader.data.metadata

import app.otakureader.core.database.dao.MangaMetadataDao
import app.otakureader.core.database.entity.MangaMetadataEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the decisions this repository makes, which are all about *when not to fetch* and *what
 * not to overwrite*. The mapping is exercised along the way rather than separately, since a mapper
 * with no caller is not the thing that breaks.
 *
 * Freshness is driven by the stored `fetchedAt` rather than by injecting a clock: `0L` is 1970 and
 * always stale, `System.currentTimeMillis()` is always fresh. That keeps the production class free
 * of a seam that exists only for tests.
 */
class AniListMetadataRepositoryTest {

    private lateinit var api: AniListMetadataApi
    private lateinit var dao: MangaMetadataDao
    private lateinit var repository: AniListMetadataRepository

    @Before
    fun setUp() {
        api = mockk()
        dao = mockk(relaxed = true)
        repository = AniListMetadataRepository(api, dao)
    }

    private fun media(
        id: Long = 99L,
        tags: List<MetadataTag> = emptyList(),
        synonyms: List<String> = emptyList(),
        title: MetadataTitle? = null,
        startDate: MetadataDate? = null,
    ) = MetadataMedia(
        id = id,
        description = "A description",
        genres = listOf("Action"),
        tags = tags,
        averageScore = 84,
        chapters = 364,
        synonyms = synonyms,
        title = title,
        startDate = startDate,
    )

    private fun cached(
        mangaId: Long = 1L,
        anilistId: Long = 99L,
        fetchedAt: Long,
    ) = MangaMetadataEntity(mangaId = mangaId, anilistId = anilistId, fetchedAt = fetchedAt)

    // ── When to fetch ────────────────────────────────────────────────────────

    @Test
    fun `a fresh cached copy is served without touching the network`() = runTest {
        coEvery { dao.getByMangaId(1L) } returns cached(fetchedAt = System.currentTimeMillis())

        val result = repository.refreshMetadata(mangaId = 1L, anilistId = 99L)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test
    fun `a stale cached copy is refetched`() = runTest {
        coEvery { dao.getByMangaId(1L) } returns cached(fetchedAt = 0L)
        coEvery { api.fetch(99L) } returns MetadataResponse(data = MetadataData(media = media()))

        repository.refreshMetadata(mangaId = 1L, anilistId = 99L)

        coVerify(exactly = 1) { api.fetch(99L) }
    }

    @Test
    fun `force refetches even when the cache is fresh`() = runTest {
        coEvery { dao.getByMangaId(1L) } returns cached(fetchedAt = System.currentTimeMillis())
        coEvery { api.fetch(99L) } returns MetadataResponse(data = MetadataData(media = media()))

        repository.refreshMetadata(mangaId = 1L, anilistId = 99L, force = true)

        coVerify(exactly = 1) { api.fetch(99L) }
    }

    /**
     * The case a TTL check alone gets wrong.
     *
     * Correcting a wrong match changes the anilistId while the mangaId stays put. If freshness were
     * the only test, the old manga's metadata would keep being served until it expired — up to a
     * week — and the user's correction would appear to do nothing at all.
     */
    @Test
    fun `a fresh cached copy for a different anilistId is refetched`() = runTest {
        coEvery { dao.getByMangaId(1L) } returns
            cached(anilistId = 11L, fetchedAt = System.currentTimeMillis())
        coEvery { api.fetch(99L) } returns MetadataResponse(data = MetadataData(media = media()))

        repository.refreshMetadata(mangaId = 1L, anilistId = 99L)

        coVerify(exactly = 1) { api.fetch(99L) }
    }

    // ── What not to overwrite ────────────────────────────────────────────────

    @Test
    fun `a network failure leaves the cache untouched`() = runTest {
        coEvery { dao.getByMangaId(1L) } returns cached(fetchedAt = 0L)
        coEvery { api.fetch(99L) } throws RuntimeException("offline")

        val result = repository.refreshMetadata(mangaId = 1L, anilistId = 99L)

        assertTrue(result.isFailure)
        // The point of the test. Yesterday's metadata is strictly better than none, so a failed
        // refresh must not clear or half-write the row.
        coVerify(exactly = 0) { dao.upsert(any()) }
        coVerify(exactly = 0) { dao.deleteByMangaId(any()) }
    }

    @Test
    fun `a GraphQL error is a failure even though the HTTP call succeeded`() = runTest {
        // AniList answers a rejected document with HTTP 200 and data: null, so nothing throws.
        coEvery { dao.getByMangaId(1L) } returns null
        coEvery { api.fetch(99L) } returns MetadataResponse(
            data = null,
            errors = listOf(MetadataError("Invalid token")),
        )

        val result = repository.refreshMetadata(mangaId = 1L, anilistId = 99L)

        assertTrue(result.isFailure)
        assertEquals("Invalid token", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `errors are checked before data, so a partial response is still a failure`() = runTest {
        // GraphQL can return both when one resolver fails and its siblings succeed. Accepting the
        // data would cache a half-filled record as though it were complete.
        coEvery { dao.getByMangaId(1L) } returns null
        coEvery { api.fetch(99L) } returns MetadataResponse(
            data = MetadataData(media = media()),
            errors = listOf(MetadataError("Failed to resolve tags")),
        )

        val result = repository.refreshMetadata(mangaId = 1L, anilistId = 99L)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `a null media is a failure rather than an empty record`() = runTest {
        coEvery { dao.getByMangaId(1L) } returns null
        coEvery { api.fetch(99L) } returns MetadataResponse(data = MetadataData(media = null))

        assertTrue(repository.refreshMetadata(mangaId = 1L, anilistId = 99L).isFailure)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    @Test
    fun `spoiler tags are dropped at the boundary`() = runTest {
        val captured = slot<MangaMetadataEntity>()
        coEvery { dao.getByMangaId(1L) } returns null
        coEvery { dao.upsert(capture(captured)) } just runs
        coEvery { api.fetch(99L) } returns MetadataResponse(
            data = MetadataData(
                media = media(
                    tags = listOf(
                        MetadataTag("Isekai", 87),
                        MetadataTag("Major Character Death", 95, isMediaSpoiler = true),
                        MetadataTag("Time Loop", 70, isGeneralSpoiler = true),
                    )
                )
            )
        )

        val result = repository.refreshMetadata(mangaId = 1L, anilistId = 99L)

        // Both flags, not just one. Checking only isMediaSpoiler would still put "Time Loop" on
        // the details page.
        assertEquals(listOf("Isekai"), captured.captured.tagNames)
        assertEquals(listOf("Isekai"), result.getOrThrow().tags.map { it.name })
        assertEquals(87, result.getOrThrow().tags.single().rank)
    }

    @Test
    fun `placeholder titles are kept out of the synonym set`() = runTest {
        coEvery { dao.getByMangaId(1L) } returns null
        coEvery { dao.upsert(any()) } just runs
        coEvery { api.fetch(99L) } returns MetadataResponse(
            data = MetadataData(
                media = media(
                    synonyms = listOf("?", "n/a", "Berserk Deluxe", "Berserk Deluxe"),
                    title = MetadataTitle(romaji = "Berserk", english = "Berserk"),
                )
            )
        )

        val synonyms = repository.refreshMetadata(mangaId = 1L, anilistId = 99L).getOrThrow().synonyms

        // Deduplicated across titles and synonyms, and the placeholders are gone — they would
        // otherwise be offered to the user as alternative names and fed to the matcher as evidence.
        assertEquals(listOf("Berserk", "Berserk Deluxe"), synonyms)
    }

    @Test
    fun `a partial date keeps only the precision AniList gave`() = runTest {
        coEvery { dao.getByMangaId(1L) } returns null
        coEvery { dao.upsert(any()) } just runs
        coEvery { api.fetch(99L) } returns MetadataResponse(
            data = MetadataData(media = media(startDate = MetadataDate(year = 1989, month = 8)))
        )

        // Not "1989-08-01": padding a missing day invents a precision that was never claimed.
        assertEquals("1989-08", repository.refreshMetadata(1L, 99L).getOrThrow().startDate)
    }

    @Test
    fun `a date with no year is null rather than a fragment`() = runTest {
        coEvery { dao.getByMangaId(1L) } returns null
        coEvery { dao.upsert(any()) } just runs
        coEvery { api.fetch(99L) } returns MetadataResponse(
            data = MetadataData(media = media(startDate = MetadataDate(month = 8, day = 12)))
        )

        assertNull(repository.refreshMetadata(1L, 99L).getOrThrow().startDate)
    }

    @Test
    fun `a tag whose rank did not survive storage is dropped, not defaulted`() = runTest {
        // The two parallel columns are the part of the denormalized encoding worth guarding: a
        // rank of "" must not become a chip claiming rank 0.
        coEvery { dao.observeByMangaId(1L) } returns flowOf(
            MangaMetadataEntity(
                mangaId = 1L,
                anilistId = 99L,
                tagNames = listOf("Isekai", "Broken"),
                tagRanks = listOf("87", ""),
            )
        )

        val metadata = repository.observeMetadata(1L).first()

        assertEquals(listOf("Isekai"), metadata!!.tags.map { it.name })
    }
}
