package app.otakureader.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each measure is tested on the case it exists for and on the case it is blind to.
 *
 * Testing only the agreements would leave three interchangeable-looking functions with no evidence
 * that any of them earns its place in the blend — and the blend's weights are only defensible if
 * the measures genuinely disagree.
 */
class StringSimilarityTest {

    // ── ratio ────────────────────────────────────────────────────────────────

    @Test
    fun `identical strings are 1`() {
        assertEquals(1f, StringSimilarity.ratio("berserk", "berserk"))
    }

    @Test
    fun `two empty strings are identical, not maximally different`() {
        // 0.0 here would propagate through the caller's weighted blend as a confusing zero for
        // input that is, in fact, equal.
        assertEquals(1f, StringSimilarity.ratio("", ""))
    }

    @Test
    fun `one empty string shares nothing`() {
        assertEquals(0f, StringSimilarity.ratio("", "berserk"))
    }

    @Test
    fun `a single typo costs one edit out of the length`() {
        // "bersek" vs "berserk": one insertion over a max length of 7.
        assertEquals(1f - 1f / 7f, StringSimilarity.ratio("bersek", "berserk"), 0.0001f)
    }

    @Test
    fun `ratio is blind to word order`() {
        // The case tokenSetRatio exists to cover: same words, rearranged, and plain edit distance
        // charges for nearly every character.
        assertTrue(StringSimilarity.ratio("love is war", "war is love") < 0.6f)
    }

    // ── partialRatio ─────────────────────────────────────────────────────────

    @Test
    fun `a title contained in a longer one scores 1`() {
        assertEquals(1f, StringSimilarity.partialRatio("berserk", "berserk deluxe edition"))
    }

    @Test
    fun `the contained match is found anywhere, not only at the start`() {
        assertEquals(1f, StringSimilarity.partialRatio("berserk", "the complete berserk archive"))
    }

    @Test
    fun `partialRatio is symmetric in its arguments`() {
        assertEquals(
            StringSimilarity.partialRatio("berserk", "berserk deluxe edition"),
            StringSimilarity.partialRatio("berserk deluxe edition", "berserk"),
        )
    }

    @Test
    fun `containment scores far higher under partialRatio than under ratio`() {
        // This gap is the whole reason both are in the blend. Asserting each in isolation would
        // not show that they disagree, which is the property that matters.
        val a = "berserk"
        val b = "berserk deluxe edition"
        assertTrue(StringSimilarity.partialRatio(a, b) - StringSimilarity.ratio(a, b) > 0.5f)
    }

    // ── tokenSetRatio ────────────────────────────────────────────────────────

    @Test
    fun `reordered words score 1`() {
        assertEquals(1f, StringSimilarity.tokenSetRatio("love is war", "war is love"))
    }

    @Test
    fun `a superset of words still scores 1`() {
        // Every token of the shorter title is present, so the sorted intersection equals the
        // shorter string exactly.
        assertEquals(1f, StringSimilarity.tokenSetRatio("hero academia", "boku no hero academia"))
    }

    @Test
    fun `repeated words do not change the result`() {
        assertEquals(
            StringSimilarity.tokenSetRatio("hero academia", "boku no hero academia"),
            StringSimilarity.tokenSetRatio("hero hero academia", "boku no hero academia academia"),
        )
    }

    @Test
    fun `tokenSetRatio is blind to a typo inside a token`() {
        // The case ratio covers and this one does not: no token matches exactly, so the sets are
        // disjoint even though a human reads them as the same words.
        assertTrue(StringSimilarity.tokenSetRatio("berserk", "bersek") < 0.9f)
        assertTrue(StringSimilarity.ratio("berserk", "bersek") > 0.8f)
    }

    @Test
    fun `disjoint word sets score low`() {
        assertTrue(StringSimilarity.tokenSetRatio("berserk", "vagabond") < 0.5f)
    }

    // ── levenshtein ──────────────────────────────────────────────────────────

    @Test
    fun `levenshtein counts single edits of each kind`() {
        assertEquals(0, StringSimilarity.levenshtein("kitten", "kitten"))
        assertEquals(1, StringSimilarity.levenshtein("kitten", "sitten")) // substitution
        assertEquals(1, StringSimilarity.levenshtein("kitten", "kitteng")) // insertion
        assertEquals(1, StringSimilarity.levenshtein("kitten", "kiten")) // deletion
        assertEquals(3, StringSimilarity.levenshtein("kitten", "sitting")) // the textbook case
    }

    @Test
    fun `levenshtein against an empty string is the other length`() {
        assertEquals(7, StringSimilarity.levenshtein("", "berserk"))
        assertEquals(7, StringSimilarity.levenshtein("berserk", ""))
    }

    @Test
    fun `levenshtein is symmetric`() {
        // The two-row implementation writes into a rolling buffer, which is where an
        // asymmetry would show up if the row swap were wrong.
        assertEquals(
            StringSimilarity.levenshtein("kaguya sama", "kaguyasama wa"),
            StringSimilarity.levenshtein("kaguyasama wa", "kaguya sama"),
        )
    }
}
