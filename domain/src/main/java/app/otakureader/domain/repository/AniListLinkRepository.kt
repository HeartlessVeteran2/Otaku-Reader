package app.otakureader.domain.repository

import app.otakureader.domain.model.AniListLink
import kotlinx.coroutines.flow.Flow

/**
 * The durable record of which AniList media each manga is.
 *
 * ### Why this is not part of [MangaMetadataRepository]
 *
 * Same reason the tables are separate: that one owns a seven-day cache whose whole contract is that
 * it can be thrown away and refetched — every refresh rewrites the row outright. A link written by
 * the user cannot be treated that way, and sharing an interface would invite some future
 * cache-wide operation to take the correction with it.
 */
interface AniListLinkRepository {

    /** The stored link for [mangaId], emitting null until something has matched it. */
    fun observeLink(mangaId: Long): Flow<AniListLink?>

    /** A one-shot read, for deciding whether auto-matching needs to run at all. */
    suspend fun getLink(mangaId: Long): AniListLink?

    /**
     * Records an automatically-derived link, **without** disturbing a user's own choice.
     *
     * The no-overwrite rule lives here rather than at the call site on purpose. Auto-matching is
     * triggered from more than one place over time, and a rule enforced at each caller is a rule
     * that one caller eventually forgets — at which point a correction silently reverts to the
     * guess it was made to fix.
     *
     * @return true when the link was written, false when a user-confirmed link was left in place.
     */
    suspend fun saveAutoLink(mangaId: Long, anilistId: Long): Boolean

    /**
     * Records the user's own choice, overwriting whatever was there.
     *
     * This is the only path that sets [AniListLink.userConfirmed], and it always wins — the user
     * looked at the candidates and picked one.
     */
    suspend fun saveUserLink(mangaId: Long, anilistId: Long)

}
