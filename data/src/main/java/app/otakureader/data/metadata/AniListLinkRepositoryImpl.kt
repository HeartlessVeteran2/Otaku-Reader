package app.otakureader.data.metadata

import app.otakureader.core.database.dao.MangaAniListLinkDao
import app.otakureader.core.database.entity.MangaAniListLinkEntity
import app.otakureader.domain.model.AniListLink
import app.otakureader.domain.repository.AniListLinkRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room-backed AniList links.
 *
 * ### Writes are serialized per manga
 *
 * [saveAutoLink] is read-decide-write — it reads the existing row, refuses if a human wrote it, and
 * upserts otherwise. Split across two statements that is a race, and the losing case is the one
 * that matters: an auto-match that reads "no user link", is descheduled while the user picks the
 * right entry in the picker, and then writes its guess over the correction.
 *
 * The lock therefore has to span the check *and* the write, not each separately. Striped rather
 * than per-id for the same reasons as [AniListMetadataRepository] — a `ConcurrentHashMap<Long,
 * Mutex>` grows for every manga ever linked, and evicting from one races the next caller that
 * already read the same instance.
 *
 * Unlike that class, nothing here holds the lock across a network call, so a single mutex would
 * have been defensible. Striping costs one array and keeps the two classes reading the same way.
 */
@Singleton
class AniListLinkRepositoryImpl @Inject constructor(
    private val dao: MangaAniListLinkDao,
) : AniListLinkRepository {

    override fun observeLink(mangaId: Long): Flow<AniListLink?> =
        dao.observeByMangaId(mangaId).map { it?.toDomain() }

    override suspend fun getLink(mangaId: Long): AniListLink? = dao.getByMangaId(mangaId)?.toDomain()

    override suspend fun saveAutoLink(mangaId: Long, anilistId: Long): Boolean =
        lockFor(mangaId).withLock {
            if (dao.getByMangaId(mangaId)?.userConfirmed == true) return@withLock false
            dao.upsert(
                MangaAniListLinkEntity(
                    mangaId = mangaId,
                    anilistId = anilistId,
                    userConfirmed = false,
                    matchedAt = System.currentTimeMillis(),
                )
            )
            true
        }

    override suspend fun saveUserLink(mangaId: Long, anilistId: Long) {
        // Under the same lock as the auto path, so an in-flight auto-match cannot land its guess
        // after this returns.
        lockFor(mangaId).withLock {
            dao.upsert(
                MangaAniListLinkEntity(
                    mangaId = mangaId,
                    anilistId = anilistId,
                    userConfirmed = true,
                    matchedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /** `floorMod`, not `%`: a negative id would otherwise index out of bounds. */
    private fun lockFor(mangaId: Long): Mutex = locks[Math.floorMod(mangaId, STRIPE_COUNT)]

    private val locks = Array(STRIPE_COUNT) { Mutex() }

    companion object {
        /** Matches [AniListMetadataRepository.STRIPE_COUNT]; collisions only cost parallelism. */
        const val STRIPE_COUNT = 64
    }
}

private fun MangaAniListLinkEntity.toDomain() = AniListLink(
    mangaId = mangaId,
    anilistId = anilistId,
    userConfirmed = userConfirmed,
    matchedAt = matchedAt,
)
