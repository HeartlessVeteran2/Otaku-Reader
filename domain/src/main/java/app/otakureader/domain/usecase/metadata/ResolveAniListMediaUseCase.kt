package app.otakureader.domain.usecase.metadata

import app.otakureader.domain.model.AniListMatch
import app.otakureader.domain.repository.AniListSearchRepository
import app.otakureader.domain.util.PlaceholderTitles
import javax.inject.Inject

/**
 * Finds which AniList media a source manga is, by searching and then scoring the results.
 *
 * [MatchAniListMediaUseCase] is a pure function over candidates it is handed; this is the half that
 * decides *what to search for* and hands them over. Keeping them apart is what lets the matcher stay
 * driven by a fixture table with no network in sight.
 *
 * ### Why searching more than once is necessary
 *
 * AniList's search is a text index, not a matcher. A source that names a work
 * `"Boku no Hero Academia (Official Colored)"` returns nothing at all — there is no entry by that
 * name — and no amount of clever scoring rescues an empty candidate list. So when a search comes
 * back empty the next known title is tried, which is exactly the case a manual override or a
 * previously-cached synonym set exists to repair.
 *
 * ### Why it stops at the first search that returns anything
 *
 * Merging results from every title would give the matcher more to choose from, and cost a request
 * per title against a rate limit this app has to wait out — up to 90 seconds per response when
 * AniList is throttling (#1236). One extra request to rescue a failed search is worth it; three
 * more to slightly widen an already-populated candidate list is not.
 *
 * Note that stopping early costs nothing in *matching* quality: whichever search produced the
 * candidates, they are scored against the whole title set, so a candidate found via a synonym is
 * still judged on how well it matches the source title.
 *
 * ### A failed search aborts the cascade
 *
 * Only an *empty* result advances to the next title. A failure means the request did not complete —
 * no network, or AniList refused the document — and the next title would fail the same way. Retrying
 * it three more times would turn one dead request into four, which is precisely what the rate-limit
 * interceptor exists to prevent.
 */
class ResolveAniListMediaUseCase @Inject constructor(
    private val searchRepository: AniListSearchRepository,
    private val matchMedia: MatchAniListMediaUseCase,
) {

    /**
     * @param sourceTitle the title as the source names it — searched first, and always scored.
     * @param alternativeTitles other names known locally, used both as fallback search terms and as
     *   additional scoring targets.
     * @return the best match, `null` when no search produced any candidate, or a failure when a
     *   search could not be completed.
     */
    suspend operator fun invoke(
        sourceTitle: String,
        alternativeTitles: List<String> = emptyList(),
    ): Result<AniListMatch?> {
        val searchTerms = (listOf(sourceTitle) + alternativeTitles)
            .map { it.trim() }
            .filter { PlaceholderTitles.isMeaningful(it) }
            // Case-insensitively distinct: a source title that differs from a synonym only in
            // capitalisation would otherwise spend a second request to receive the same page.
            //
            // The no-argument `lowercase()` is already locale-invariant — Kotlin defines it as the
            // invariant-locale mapping, which is exactly why it replaced `toLowerCase()`. Passing
            // `Locale.ROOT` explicitly would be a no-op, and passing `Locale.getDefault()` would
            // introduce the Turkish dotless-i bug rather than avoid it.
            .distinctBy { it.lowercase() }
            .take(MAX_SEARCH_ATTEMPTS)

        if (searchTerms.isEmpty()) return Result.success(null)

        for (term in searchTerms) {
            val candidates = searchRepository.searchMedia(term)
                .getOrElse { return Result.failure(it) }
            if (candidates.isEmpty()) continue

            return Result.success(
                matchMedia(
                    sourceTitle = sourceTitle,
                    alternativeTitles = alternativeTitles,
                    candidates = candidates,
                )
            )
        }
        return Result.success(null)
    }

    companion object {
        /**
         * How many titles are searched before giving up.
         *
         * Four covers the shapes that actually occur — a source title plus an english, romaji and
         * native alternative — without letting a manga with twenty cached synonyms issue twenty
         * requests.
         */
        const val MAX_SEARCH_ATTEMPTS = 4
    }
}
