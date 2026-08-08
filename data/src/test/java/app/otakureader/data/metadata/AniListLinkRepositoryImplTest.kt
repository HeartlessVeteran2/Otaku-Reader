package app.otakureader.data.metadata

import app.otakureader.core.database.dao.MangaAniListLinkDao
import app.otakureader.core.database.entity.MangaAniListLinkEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the no-overwrite rule against a real implementation rather than a mock.
 *
 * The ViewModel tests stub this repository, so they prove the ViewModel *calls* `saveAutoLink` —
 * not that `saveAutoLink` protects anything. The protection is the point of the table.
 */
class AniListLinkRepositoryImplTest {

    private val mangaId = 42L

    /** Enough virtual time that an unlocked read-then-write is guaranteed to interleave. */
    private val slowRead = 1_000L

    private class FakeLinkDao(private val readDelayMs: Long = 0L) : MangaAniListLinkDao {
        private val rows = MutableStateFlow<Map<Long, MangaAniListLinkEntity>>(emptyMap())

        override fun observeByMangaId(mangaId: Long): Flow<MangaAniListLinkEntity?> =
            rows.map { it[mangaId] }

        override suspend fun getByMangaId(mangaId: Long): MangaAniListLinkEntity? {
            // Snapshot first, *then* delay. This models the hazard that actually exists: a read
            // observes some state, and by the time the caller acts on what it saw, the world has
            // moved on. Delaying before the snapshot instead would return post-interleaving data,
            // which quietly makes the unlocked implementation look safe — the first version of
            // this fake did exactly that and the race test passed with the lock deleted.
            val snapshot = rows.value[mangaId]
            if (readDelayMs > 0) delay(readDelayMs)
            return snapshot
        }

        override suspend fun upsert(link: MangaAniListLinkEntity) {
            rows.value = rows.value + (link.mangaId to link)
        }

        override suspend fun deleteByMangaId(mangaId: Long) {
            rows.value = rows.value - mangaId
        }
    }

    @Test
    fun `saveAutoLink writes when nothing is stored`() = runTest {
        val repository = AniListLinkRepositoryImpl(FakeLinkDao())

        assertTrue(repository.saveAutoLink(mangaId, 53390L))

        val link = repository.getLink(mangaId)
        assertEquals(53390L, link?.anilistId)
        assertFalse("auto-matching must never claim a link was user-confirmed", link!!.userConfirmed)
    }

    @Test
    fun `saveAutoLink replaces an earlier automatic link`() = runTest {
        val repository = AniListLinkRepositoryImpl(FakeLinkDao())
        repository.saveAutoLink(mangaId, 1L)

        assertTrue(repository.saveAutoLink(mangaId, 2L))
        assertEquals(2L, repository.getLink(mangaId)?.anilistId)
    }

    @Test
    fun `saveAutoLink refuses to replace a user-confirmed link`() = runTest {
        val repository = AniListLinkRepositoryImpl(FakeLinkDao())
        repository.saveUserLink(mangaId, 53390L)

        // The rule the whole table exists for: auto-matching runs whenever a manga has a link it
        // did not write, so without this it would overwrite a correction with the same wrong guess
        // that made the correction necessary.
        assertFalse(repository.saveAutoLink(mangaId, 99999L))
        assertEquals(53390L, repository.getLink(mangaId)?.anilistId)
        assertTrue(repository.getLink(mangaId)!!.userConfirmed)
    }

    @Test
    fun `saveUserLink always wins, even over an existing automatic link`() = runTest {
        val repository = AniListLinkRepositoryImpl(FakeLinkDao())
        repository.saveAutoLink(mangaId, 1L)

        repository.saveUserLink(mangaId, 2L)

        val link = repository.getLink(mangaId)
        assertEquals(2L, link?.anilistId)
        assertTrue(link!!.userConfirmed)
    }

    @Test
    fun `an in-flight auto-match cannot land its guess over a user's pick`() = runTest {
        // The losing interleaving, made deterministic: saveAutoLink reads "no user link", is
        // descheduled across the read delay while the user picks the right entry, and then writes.
        // Without a lock spanning the check *and* the write, the guess lands last and the
        // correction is gone.
        val repository = AniListLinkRepositoryImpl(FakeLinkDao(readDelayMs = slowRead))

        val auto = launch { repository.saveAutoLink(mangaId, 99999L) }
        val user = launch { repository.saveUserLink(mangaId, 53390L) }
        auto.join()
        user.join()

        val link = repository.getLink(mangaId)
        assertEquals("the user's pick must survive", 53390L, link?.anilistId)
        assertTrue(link!!.userConfirmed)
    }

    @Test
    fun `clearLink forgets the link so the next visit re-matches`() = runTest {
        val repository = AniListLinkRepositoryImpl(FakeLinkDao())
        repository.saveUserLink(mangaId, 53390L)

        repository.clearLink(mangaId)

        assertNull(repository.getLink(mangaId))
    }

    @Test
    fun `links for different manga do not interfere`() = runTest {
        // Locks are striped, so two ids can share a mutex. That may cost parallelism; it must never
        // cost correctness.
        val repository = AniListLinkRepositoryImpl(FakeLinkDao())

        repository.saveUserLink(1L, 100L)
        repository.saveAutoLink(1L + AniListLinkRepositoryImpl.STRIPE_COUNT, 200L)

        assertEquals(100L, repository.getLink(1L)?.anilistId)
        assertEquals(200L, repository.getLink(1L + AniListLinkRepositoryImpl.STRIPE_COUNT)?.anilistId)
    }
}
