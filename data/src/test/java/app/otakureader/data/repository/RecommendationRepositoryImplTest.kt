package app.otakureader.data.repository

import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.RecommendationDao
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.SourceManga
import app.otakureader.sourceapi.toSourceId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers candidate seeding, which is the part that touches source keys.
 *
 * Seeding was dead before the key round-trip was fixed: it stringified each library row's hashed
 * key, got a decimal that matched no source, and then discarded whatever came back. Every test
 * here would have failed against that version.
 */
class RecommendationRepositoryImplTest {

    private lateinit var recommendationDao: RecommendationDao
    private lateinit var mangaDao: MangaDao
    private lateinit var sourceRepository: SourceRepository
    private lateinit var repository: RecommendationRepositoryImpl

    @Before
    fun setUp() {
        recommendationDao = mockk(relaxed = true)
        mangaDao = mockk(relaxed = true)
        sourceRepository = mockk(relaxed = true)
        coEvery { mangaDao.getRecommendationCandidates(any()) } returns emptyList()
        repository = RecommendationRepositoryImpl(recommendationDao, mangaDao, sourceRepository)
    }

    /**
     * The user's own manga must not come back as a recommendation.
     *
     * The trap is that the library row here is a *legacy* one — it stores the parsed key, while a
     * freshly seeded candidate is written under the canonical hashed key. The two therefore do
     * not collide on the `(sourceId, url)` unique index, so `insertIfNotExists` would insert a
     * second, non-favourite copy, and nothing downstream filters it: the library exclusion is by
     * row id, and the new row has a different one.
     */
    @Test
    fun `a manga already in the library is not seeded again under the canonical key`() = runTest {
        val legacyKey = SOURCE_ID.toLong()
        val library = libraryOf(sourceKey = legacyKey, ownedUrl = "/manga/owned")
        stubSource(legacyKey)
        coEvery { sourceRepository.getPopularManga(SOURCE_ID, 1) } returns Result.success(
            MangaPage(
                mangas = listOf(
                    SourceManga(url = "/manga/owned", title = "Owned", genre = "Action"),
                    SourceManga(url = "/manga/fresh", title = "Fresh", genre = "Action"),
                ),
                hasNextPage = false,
            )
        )

        repository.refreshRecommendations(library)

        val inserted = slot<List<MangaEntity>>()
        coVerify { mangaDao.insertIfNotExists(capture(inserted)) }
        assertEquals(listOf("/manga/fresh"), inserted.captured.map { it.url })
        // And what does get seeded carries the canonical key, so later adds recognise it.
        assertEquals(SOURCE_ID.toSourceId(), inserted.captured.single().sourceId)
    }

    /**
     * `MAX_SOURCES_TO_SEED` is 5. Six library sources where the first five cannot be resolved
     * used to mean nothing was seeded at all, because the cap was applied to raw keys before
     * anything checked whether they led anywhere.
     */
    @Test
    fun `unresolvable sources do not consume the seed budget`() = runTest {
        val deadKeys = (1L..5L).toList()
        val liveKey = SOURCE_ID.toLong()
        val library = deadKeys.flatMap { key -> libraryOf(sourceKey = key, ownedUrl = "/dead$key") } +
            libraryOf(sourceKey = liveKey, ownedUrl = "/manga/owned")
        deadKeys.forEach { coEvery { sourceRepository.getSourceByKey(it) } returns null }
        stubSource(liveKey)
        coEvery { sourceRepository.getPopularManga(SOURCE_ID, 1) } returns Result.success(
            MangaPage(listOf(SourceManga(url = "/manga/fresh", title = "Fresh", genre = "Action")), false)
        )

        repository.refreshRecommendations(library)

        val inserted = slot<List<MangaEntity>>()
        coVerify { mangaDao.insertIfNotExists(capture(inserted)) }
        assertTrue(inserted.captured.isNotEmpty())
    }

    private fun stubSource(key: Long) {
        coEvery { sourceRepository.getSourceByKey(key) } returns mockk(relaxed = true) {
            every { id } returns SOURCE_ID
        }
    }

    /** [MIN_LIBRARY_SIZE] is 5, so every fixture needs at least that many rows to get past it. */
    private fun libraryOf(sourceKey: Long, ownedUrl: String): List<Manga> =
        (0 until 5).map { index ->
            Manga(
                id = sourceKey * 100 + index,
                sourceId = sourceKey,
                url = if (index == 0) ownedUrl else "$ownedUrl-$index",
                title = "Owned $sourceKey-$index",
                genre = listOf("Action"),
                favorite = true,
            )
        }

    private companion object {
        /** An APK extension's id: a Tachiyomi Long, stringified. */
        const val SOURCE_ID = "2499283573021220255"
    }
}
