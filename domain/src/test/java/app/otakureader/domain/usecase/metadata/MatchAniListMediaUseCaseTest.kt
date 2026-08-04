package app.otakureader.domain.usecase.metadata

import app.otakureader.domain.model.AniListMediaCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the matcher from a table of cases that actually occur, rather than from invented strings.
 *
 * The bar for each case is "would a human looking at these two titles say they are the same manga",
 * and the matcher has to agree. Cases where a human would say *no* matter more than the easy
 * agreements, so the sequel cases below are the ones to keep working.
 */
class MatchAniListMediaUseCaseTest {

    private val match = MatchAniListMediaUseCase()

    private fun candidate(
        id: Long,
        romaji: String = "",
        english: String? = null,
        native: String? = null,
        synonyms: List<String> = emptyList(),
    ) = AniListMediaCandidate(id, romaji, english, native, synonyms)

    @Test
    fun `an exact romaji title wins`() {
        val result = match(
            sourceTitle = "Berserk",
            candidates = listOf(candidate(1, romaji = "Berserk"), candidate(2, romaji = "Bastard")),
        )

        assertEquals(1L, result!!.candidate.mediaId)
        assertTrue(result.confident)
    }

    @Test
    fun `the english title matches when the source uses romaji`() {
        // The everyday case: sources name things in romaji, AniList's English title is a
        // translation with no characters in common beyond "Hero".
        val result = match(
            sourceTitle = "Boku no Hero Academia",
            candidates = listOf(
                candidate(1, romaji = "Kimetsu no Yaiba", english = "Demon Slayer"),
                candidate(2, romaji = "Boku no Hero Academia", english = "My Hero Academia"),
            ),
        )

        assertEquals(2L, result!!.candidate.mediaId)
        assertTrue(result.confident)
    }

    @Test
    fun `a synonym matches when neither main title does`() {
        val result = match(
            sourceTitle = "OPM",
            candidates = listOf(
                candidate(1, romaji = "One Punch-Man", english = "One-Punch Man", synonyms = listOf("OPM")),
                candidate(2, romaji = "Onepunch-Man Gaiden"),
            ),
        )

        assertEquals(1L, result!!.candidate.mediaId)
    }

    /**
     * The case the season adjustment exists for, and the one a pure string measure gets wrong.
     *
     * These two titles differ by one token out of six. Without the season term the sequel scores
     * about as well as the original, and which one wins comes down to search-result order — while
     * they are different entries with different chapter counts, so the user sees their progress
     * land on the wrong one.
     */
    @Test
    fun `a season 2 source title does not match the season 1 entry`() {
        val result = match(
            sourceTitle = "Kaguya-sama: Love is War Season 2",
            candidates = listOf(
                candidate(1, romaji = "Kaguya-sama wa Kokurasetai", english = "Kaguya-sama: Love is War"),
                candidate(2, english = "Kaguya-sama: Love is War Season 2"),
            ),
        )

        assertEquals(2L, result!!.candidate.mediaId)
    }

    @Test
    fun `an unmarked source title prefers the unmarked entry over a sequel`() {
        val result = match(
            sourceTitle = "Overlord",
            candidates = listOf(
                candidate(1, romaji = "Overlord 2"),
                candidate(2, romaji = "Overlord"),
            ),
        )

        assertEquals(2L, result!!.candidate.mediaId)
    }

    @Test
    fun `the ordinal season form is understood too`() {
        // "2nd Season" is as common as "Season 2" in AniList's own titles.
        val result = match(
            sourceTitle = "Shingeki no Kyojin 2nd Season",
            candidates = listOf(
                candidate(1, romaji = "Shingeki no Kyojin"),
                candidate(2, romaji = "Shingeki no Kyojin 2nd Season"),
            ),
        )

        assertEquals(2L, result!!.candidate.mediaId)
    }

    @Test
    fun `punctuation and spacing differences still match`() {
        val result = match(
            sourceTitle = "Re:Zero kara Hajimeru Isekai Seikatsu",
            candidates = listOf(
                candidate(1, romaji = "ReZero kara Hajimeru Isekai Seikatsu"),
                candidate(2, romaji = "Tensei Shitara Slime Datta Ken"),
            ),
        )

        assertEquals(1L, result!!.candidate.mediaId)
        assertTrue(result.confident)
    }

    @Test
    fun `a subtitle on the candidate does not prevent a match`() {
        // The distractor is a different manga, not a typo of the same one. An earlier version of
        // this test used "Bersek" as the distractor, which a human would also call Berserk — so
        // whichever way it resolved, the test proved nothing about edition suffixes.
        val result = match(
            sourceTitle = "Berserk",
            candidates = listOf(candidate(1, english = "Berserk: Deluxe Edition"), candidate(2, romaji = "Bastard!!")),
        )

        assertEquals(1L, result!!.candidate.mediaId)
        assertTrue(result.confident)
    }

    @Test
    fun `placeholder titles are ignored rather than matched against each other`() {
        // Sources emit "?" for a missing localized name. Two of them are a perfect string match
        // and a completely meaningless one.
        val result = match(
            sourceTitle = "Vinland Saga",
            candidates = listOf(
                candidate(1, romaji = "?", english = "?"),
                candidate(2, romaji = "Vinland Saga"),
            ),
        )

        assertEquals(2L, result!!.candidate.mediaId)
    }

    @Test
    fun `an alternative title matches when the source title does not`() {
        // This is the manual-override path: the user has told us what this manga is really called.
        val result = match(
            sourceTitle = "Zzzz Unknown Release",
            alternativeTitles = listOf("Vagabond"),
            candidates = listOf(candidate(1, romaji = "Berserk"), candidate(2, romaji = "Vagabond")),
        )

        assertEquals(2L, result!!.candidate.mediaId)
    }

    @Test
    fun `an unrelated title still returns the best candidate but not as confident`() {
        // Never returning null for a non-empty candidate list is deliberate — the caller shows a
        // picker, and "here is my best guess, marked uncertain" beats an empty screen. The flag is
        // what keeps a guess from being written to the manga row as a confirmed match.
        val result = match(
            sourceTitle = "Completely Unrelated Manga Title",
            candidates = listOf(candidate(1, romaji = "Berserk")),
        )

        assertEquals(1L, result!!.candidate.mediaId)
        assertFalse(result.confident)
    }

    /**
     * A season stated only in an override has to count.
     *
     * The season used to be read from `sourceTitle` alone. So when the source name is junk — which
     * is the whole reason an override exists — the season term was silently switched off, the
     * entry and its sequel tied on base score (normalization strips the marker from both), and the
     * winner fell to search order. Every target title now carries its own season.
     */
    @Test
    fun `a season stated in an alternative title disambiguates the sequel`() {
        val result = match(
            sourceTitle = "kaguya raw v2 scan",
            alternativeTitles = listOf("Kaguya-sama: Love is War Season 2"),
            candidates = listOf(
                candidate(1, english = "Kaguya-sama: Love is War"),
                candidate(2, english = "Kaguya-sama: Love is War Season 2"),
            ),
        )

        assertEquals(2L, result!!.candidate.mediaId)
    }

    /**
     * The season that counts is the one on the title that matched.
     *
     * The adjustment used to be computed once per candidate, from the first of its titles stating
     * any season. An entry whose romaji says one season and whose english says another was then
     * judged by whichever came first in the list, even when the *other* title was the exact match.
     * Asserting the score rather than the winner, because that is where the difference shows:
     * under the old rule the romaji's season 5 disagreed with the target's 2 and cost 0.3.
     */
    @Test
    fun `the season comes from the matched title, not from a sibling title`() {
        val result = match(
            sourceTitle = "Overlord Season 2",
            candidates = listOf(
                candidate(1, romaji = "Overlord Season 5", english = "Overlord Season 2"),
            ),
        )

        assertEquals(1f, result!!.score)
        assertTrue(result.confident)
    }

    @Test
    fun `a trailing volume number is not read as a season`() {
        // "Berserk Vol 3" is the same manga as "Berserk". Reading the 3 as a season would put a
        // 0.3 penalty on the correct entry, which is worse than having no season signal at all.
        val result = match(
            sourceTitle = "Berserk Vol 3",
            candidates = listOf(candidate(1, romaji = "Berserk"), candidate(2, romaji = "Bastard!!")),
        )

        assertEquals(1L, result!!.candidate.mediaId)
        assertTrue(result.confident)
    }

    @Test
    fun `a lowercase placeholder is filtered too`() {
        // The check used to be case-sensitive, so "n/a" slipped past "N/A" and could match another
        // placeholder perfectly.
        val result = match(
            sourceTitle = "Vinland Saga",
            candidates = listOf(candidate(1, romaji = "n/a", english = "n/a"), candidate(2, romaji = "Vinland Saga")),
        )

        assertEquals(2L, result!!.candidate.mediaId)
    }

    @Test
    fun `no candidates yields null`() {
        assertNull(match(sourceTitle = "Berserk", candidates = emptyList()))
    }

    @Test
    fun `a blank source title with no alternatives yields null`() {
        // Rather than matching everything equally badly and picking arbitrarily.
        assertNull(match(sourceTitle = "   ", candidates = listOf(candidate(1, romaji = "Berserk"))))
    }

    @Test
    fun `a candidate with no usable titles scores zero rather than throwing`() {
        val result = match(
            sourceTitle = "Berserk",
            candidates = listOf(candidate(1), candidate(2, romaji = "Berserk")),
        )

        assertEquals(2L, result!!.candidate.mediaId)
    }
}
