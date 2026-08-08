package app.otakureader.domain.repository

import app.otakureader.domain.model.AniListMediaCandidate

/**
 * Searches AniList for media that might correspond to a local manga.
 *
 * ### Separate from [MangaMetadataRepository] on purpose
 *
 * That one owns a *cache*: it reads Room, writes Room, and has a TTL. This one owns no state at
 * all — every call is a network round trip, nothing is stored, and there is nothing to observe.
 * Folding them together would put a method with none of the offline-first guarantees the metadata
 * repository documents behind the same interface that makes them.
 *
 * The two are still wired to the same Retrofit instance, so a search inherits the rate limiter and
 * certificate pinning without restating either.
 */
interface AniListSearchRepository {

    /**
     * Candidates whose title resembles [title], best-effort and unranked.
     *
     * Ranking is [app.otakureader.domain.usecase.metadata.MatchAniListMediaUseCase]'s job — AniList
     * orders by its own relevance, which knows nothing about the alternative titles the local
     * record carries.
     *
     * @return the candidates, or a failure carrying why the search did not happen.
     *
     * An empty list means **no candidates**, and deliberately does not say why. It covers two
     * paths: AniList looked and had nothing under that name — normal for a long-tail source title,
     * and not an error — and the title being unusable enough that it was never sent, which
     * implementations may short-circuit locally rather than spend a request against the rate limit
     * to be told the same thing. Callers treat both identically (move on to the next title), so
     * naming only the first would be a claim the contract does not keep.
     *
     * A malformed response is a **failure**, not an empty list. "AniList found nothing" and
     * "AniList did not answer the question asked" have to stay distinguishable, because the caller
     * advances on the first and aborts on the second.
     */
    suspend fun searchMedia(title: String): Result<List<AniListMediaCandidate>>
}
