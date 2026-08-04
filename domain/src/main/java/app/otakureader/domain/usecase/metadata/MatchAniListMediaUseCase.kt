package app.otakureader.domain.usecase.metadata

import app.otakureader.domain.model.AniListMatch
import app.otakureader.domain.model.AniListMediaCandidate
import app.otakureader.domain.util.PlaceholderTitles
import app.otakureader.domain.util.StringSimilarity
import app.otakureader.domain.util.TitleNormalizer
import javax.inject.Inject

/**
 * Picks the AniList media that corresponds to a manga from a source.
 *
 * ### Why this is hard enough to need scoring
 *
 * A source calls it `"Boku no Hero Academia"`; AniList's English title is `"My Hero Academia"`;
 * another source says `"僕のヒーローアカデミア"`; a third appends `"(Official Colored)"`. There is no
 * identifier in common — only strings that a human would recognise as the same work. So every
 * candidate is scored against **every** title the target is known by, and the best takes it.
 *
 * ### Scoring
 *
 * Three measures blended, because each is blind to something the others catch (see
 * [StringSimilarity]):
 *
 * ```
 * 0.4 * tokenSetRatio  +  0.3 * partialRatio  +  0.3 * ratio
 * ```
 *
 * Then a season adjustment of ±[SEASON_WEIGHT]. Sequels are the failure case that a pure string
 * measure cannot survive: `"Kaguya-sama: Love is War"` and `"Kaguya-sama: Love is War Season 2"`
 * differ by one token out of six and would otherwise be near-indistinguishable — while being
 * different entries with different chapter counts, which is precisely the mistake a user notices.
 *
 * ### Season numbers come from the raw title, and belong to the title
 *
 * [TitleNormalizer.normalize] **strips a trailing `season N` / `part N`** as noise, which is right
 * for its own purpose and fatal here: extracting the season after normalizing would find nothing
 * for every title, the bonus would never fire, and the code would look like it handled sequels.
 * So [seasonOf] reads the raw string, and every [TitleForm] carries its own season.
 *
 * Per *title*, not per manga or per candidate — an entry's romaji and english titles can disagree
 * about which season they name, and the one that counts is the one that actually matched.
 *
 * ### Every candidate is scored against the whole title set at once
 *
 * Not one title at a time with an early return. Scoring `savedTitle` against all candidates, then
 * `english` against all candidates, and so on, lets a weak match on an early title beat a strong
 * match on a later one purely because it was checked first.
 */
class MatchAniListMediaUseCase @Inject constructor() {

    /**
     * @param sourceTitle the title as the source names it — the one the user actually sees.
     * @param alternativeTitles any other names known locally (a manual override, a previous match).
     * @param candidates AniList search results to choose between.
     * @return the best match, or null when [candidates] is empty.
     */
    operator fun invoke(
        sourceTitle: String,
        alternativeTitles: List<String> = emptyList(),
        candidates: List<AniListMediaCandidate>,
    ): AniListMatch? {
        if (candidates.isEmpty()) return null

        val targets = buildTitleSet(listOf(sourceTitle) + alternativeTitles)
        if (targets.isEmpty()) return null

        // Ranked on the *unclamped* score, and every candidate is scored — both for the same
        // reason. `TitleNormalizer.normalize` strips the season marker, so a title and its sequel
        // normalize to the same string and both reach a base of 1.0; the season term is the only
        // thing that separates them. Clamping to 1.0 before comparing would erase exactly that
        // term, and an early exit on the first 1.0 would return whichever the search happened to
        // list first. The clamp still applies to the score that is *reported* — a caller comparing
        // against a threshold should not see 1.3.
        val best = candidates
            .map { it to rawScore(it, targets) }
            .maxByOrNull { (_, score) -> score }
            ?: return null

        val (candidate, raw) = best
        return AniListMatch(
            candidate = candidate,
            score = raw.coerceIn(0f, 1f),
            confident = raw >= ACCEPT_THRESHOLD,
        )
    }

    /**
     * The best score over every (target title, candidate title) pair, deliberately not clamped.
     *
     * **The season term belongs to the pair, not to the candidate.** Deriving it once per candidate
     * — from whichever of its titles happened to be listed first — applied one title's season to a
     * match made by a different title: an entry whose romaji says `Season 5` while its english says
     * `Season 2` was judged season 5 even when the english title was what matched. Scoring each
     * pair whole removes the question.
     *
     * It also fixes the mirror-image problem on the target side for free. The season used to come
     * from `sourceTitle` alone, so a season stated in an `alternativeTitles` override — the manual
     * "this is really X" path — contributed nothing, which disabled the season term in exactly the
     * case the override exists to repair.
     */
    private fun rawScore(candidate: AniListMediaCandidate, targets: List<TitleForm>): Float {
        val candidateTitles = buildTitleSet(
            listOfNotNull(candidate.romaji, candidate.english, candidate.native) + candidate.synonyms
        )
        if (candidateTitles.isEmpty()) return 0f

        var best = 0f
        for (target in targets) {
            for (candidateTitle in candidateTitles) {
                val paired = similarity(target, candidateTitle) +
                    seasonAdjustment(target.season, candidateTitle.season)
                best = maxOf(best, paired)
            }
        }
        return best
    }

    private fun similarity(a: TitleForm, b: TitleForm): Float {
        val light = WEIGHT_TOKEN_SET * StringSimilarity.tokenSetRatio(a.normalized, b.normalized) +
            WEIGHT_PARTIAL * StringSimilarity.partialRatio(a.normalized, b.normalized) +
            WEIGHT_RATIO * StringSimilarity.ratio(a.normalized, b.normalized)
        // The heavy form is a fallback, not a replacement: it discards spacing and the word
        // "season" entirely, which rescues "Re:Zero kara Hajimeru" vs "ReZero kara Hajimeru" but
        // would also flatten distinctions the light form keeps. Taking the max means it can only
        // ever help.
        val heavy = StringSimilarity.ratio(a.heavy, b.heavy)
        return maxOf(light, heavy)
    }

    /**
     * A bonus when the seasons agree, a penalty when they disagree, nothing when either is unknown.
     *
     * "Unknown" has to mean neutral rather than "season 1". Most titles carry no season marker at
     * all, so treating absence as 1 would penalise every unmarked title against every sequel — the
     * common case made worse to handle the rare one.
     */
    private fun seasonAdjustment(target: Int?, candidate: Int?): Float = when {
        target == null || candidate == null -> 0f
        target == candidate -> SEASON_WEIGHT
        else -> -SEASON_WEIGHT
    }

    /**
     * The season number stated in [rawTitle], or null when it states none.
     *
     * Reads the **raw** title deliberately — see the class docs.
     *
     * The bare-trailing-number form (`"Overlord 2"`) is the loose one, and it is deliberately
     * refused when the number is introduced by a unit word. Manga titles end in numbers for all
     * sorts of reasons — `"… Vol 3"`, `"… Part 2"`, `"… Chapter 12"` — and reading one of those as
     * a season is worse than missing a real season: a wrong season produces a ±0.3 swing that can
     * flip an otherwise correct match, while a missed one merely leaves the term neutral.
     */
    private fun seasonOf(rawTitle: String): Int? {
        val lower = rawTitle.lowercase()
        ORDINAL_SEASON.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        SEASON_NUMBER.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        if (UNIT_QUALIFIED_NUMBER.containsMatchIn(lower)) return null
        TRAILING_NUMBER.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }

    /**
     * Both normalizations of a title plus the season it states, computed once.
     *
     * The season rides along here because it has to be read from the **raw** title:
     * `TitleNormalizer.normalize` strips a trailing `season N`, so by the time a `TitleForm` exists
     * the marker is gone from both normalized forms.
     */
    private data class TitleForm(val normalized: String, val heavy: String, val season: Int?)

    private fun buildTitleSet(titles: List<String>): List<TitleForm> =
        titles.asSequence()
            .filter { PlaceholderTitles.isMeaningful(it) }
            .map { raw ->
                // Normalized once and reused. `heavyNormalize` used to call `TitleNormalizer`
                // again, running its full regex chain twice per title on every candidate set.
                val normalized = TitleNormalizer.normalize(raw)
                TitleForm(
                    normalized = normalized,
                    heavy = heavyNormalize(normalized),
                    season = seasonOf(raw),
                )
            }
            .filter { it.normalized.isNotEmpty() || it.heavy.isNotEmpty() }
            .distinct()
            .toList()

    /** Strips the word "season" and every non-alphanumeric character from an already-normalized title. */
    private fun heavyNormalize(normalized: String): String =
        normalized.replace(SEASON_WORD, " ").replace(NON_ALPHANUMERIC, "")

    companion object {
        const val WEIGHT_TOKEN_SET = 0.4f
        const val WEIGHT_PARTIAL = 0.3f
        const val WEIGHT_RATIO = 0.3f

        /** How much agreeing (or disagreeing) on a season moves the score. */
        const val SEASON_WEIGHT = 0.3f

        /** At or above this, the match is reported as [AniListMatch.confident]. */
        const val ACCEPT_THRESHOLD = 0.7f

        private val ORDINAL_SEASON = Regex("""(\d+)(?:st|nd|rd|th)\s+season""")
        private val SEASON_NUMBER = Regex("""season\s+(\d+)""")

        /**
         * A trailing number introduced by a unit word, which is therefore *not* a season.
         * `"Berserk Vol 3"` and `"Gantz Chapter 12"` are the same manga as their unmarked forms.
         */
        private val UNIT_QUALIFIED_NUMBER =
            Regex("""\b(?:vol|volume|part|pt|ch|chapter|book|arc|ep|episode)\.?\s*\d+\s*$""")
        private val TRAILING_NUMBER = Regex("""\s(\d{1,2})\s*$""")
        private val SEASON_WORD = Regex("""\bseason\b""")
        private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]""")
    }
}
