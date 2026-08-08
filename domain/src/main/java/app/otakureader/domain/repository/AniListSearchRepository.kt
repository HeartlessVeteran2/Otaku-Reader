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
     * @return the candidates, or a failure carrying why the search did not happen. An empty list is
     *   a success: AniList genuinely has nothing under that name, which is a normal outcome for a
     *   long-tail source title and must not be reported as an error.
     */
    suspend fun searchMedia(title: String): Result<List<AniListMediaCandidate>>
}
