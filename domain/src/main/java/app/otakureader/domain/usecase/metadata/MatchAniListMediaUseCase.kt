package app.otakureader.domain.usecase.metadata

import app.otakureader.domain.model.AniListMatch
import app.otakureader.domain.model.AniListMediaCandidate
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
 * ### Season numbers come from the raw title, not the normalized one
 *
 * [TitleNormalizer.normalize] **strips a trailing `season N` / `part N`** as noise, which is right
 * for its own purpose and fatal here: extracting the season after normalizing would find nothing
 * for every title, the bonus would never fire, and the code would look like it handled sequels.
 * So [seasonOf] reads the raw string, and normalization happens afterwards.
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
        val targetSeason = seasonOf(sourceTitle)

        // Ranked on the *unclamped* score, and every candidate is scored — both for the same
        // reason. `TitleNormalizer.normalize` strips the season marker, so a title and its sequel
        // normalize to the same string and both reach a base of 1.0; the season term is the only
        // thing that separates them. Clamping to 1.0 before comparing would erase exactly that
        // term, and an early exit on the first 1.0 would return whichever the search happened to
        // list first. The clamp still applies to the score that is *reported* — a caller comparing
        // against a threshold should not see 1.3.
        val best = candidates
            .map { it to rawScore(it, targets, targetSeason) }
            .maxByOrNull { (_, score) -> score }
            ?: return null

        val (candidate, raw) = best
        return AniListMatch(
            candidate = candidate,
            score = raw.coerceIn(0f, 1f),
            confident = raw >= ACCEPT_THRESHOLD,
        )
    }

    /** Similarity plus the season term, deliberately not clamped — see [invoke]. */
    private fun rawScore(
        candidate: AniListMediaCandidate,
        targets: List<TitleForm>,
        targetSeason: Int?,
    ): Float {
        val candidateTitles = buildTitleSet(
            listOfNotNull(candidate.romaji, candidate.english, candidate.native) + candidate.synonyms
        )
        if (candidateTitles.isEmpty()) return 0f

        var best = 0f
        for (target in targets) {
            for (candidateTitle in candidateTitles) {
                best = maxOf(best, similarity(target, candidateTitle))
            }
        }
        return best + seasonAdjustment(targetSeason, candidate.seasonOfAny())
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

    private fun AniListMediaCandidate.seasonOfAny(): Int? =
        (listOfNotNull(romaji, english, native) + synonyms).firstNotNullOfOrNull { seasonOf(it) }

    /**
     * The season number stated in [rawTitle], or null when it states none.
     *
     * Reads the **raw** title deliberately — see the class docs. The three forms are the ones that
     * actually appear in the wild: `"2nd Season"`, `"Season 2"`, and a bare trailing number as in
     * `"Overlord 2"`.
     */
    private fun seasonOf(rawTitle: String): Int? {
        val lower = rawTitle.lowercase()
        ORDINAL_SEASON.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        SEASON_NUMBER.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        TRAILING_NUMBER.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }

    /** Both normalizations of a title, computed once so the inner loops don't redo them. */
    private data class TitleForm(val normalized: String, val heavy: String)

    private fun buildTitleSet(titles: List<String>): List<TitleForm> =
        titles.asSequence()
            .filter { it.isNotBlank() && it.trim() !in PLACEHOLDER_TITLES }
            .map { TitleForm(normalized = TitleNormalizer.normalize(it), heavy = heavyNormalize(it)) }
            .filter { it.normalized.isNotEmpty() || it.heavy.isNotEmpty() }
            .distinct()
            .toList()

    /** Normalized, then stripped of the word "season" and every non-alphanumeric character. */
    private fun heavyNormalize(title: String): String =
        TitleNormalizer.normalize(title)
            .replace(SEASON_WORD, " ")
            .replace(NON_ALPHANUMERIC, "")

    companion object {
        const val WEIGHT_TOKEN_SET = 0.4f
        const val WEIGHT_PARTIAL = 0.3f
        const val WEIGHT_RATIO = 0.3f

        /** How much agreeing (or disagreeing) on a season moves the score. */
        const val SEASON_WEIGHT = 0.3f

        /** At or above this, the match is reported as [AniListMatch.confident]. */
        const val ACCEPT_THRESHOLD = 0.7f

        /**
         * Titles that carry no information and would otherwise match each other perfectly.
         * Sources really do emit these for entries with a missing localized name.
         */
        private val PLACEHOLDER_TITLES = setOf("?", "??", "???", "-", "N/A")

        private val ORDINAL_SEASON = Regex("""(\d+)(?:st|nd|rd|th)\s+season""")
        private val SEASON_NUMBER = Regex("""season\s+(\d+)""")
        private val TRAILING_NUMBER = Regex("""\s(\d{1,2})\s*$""")
        private val SEASON_WORD = Regex("""\bseason\b""")
        private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]""")
    }
}
