package app.otakureader.domain.util

/**
 * String similarity measures used for matching manga titles across services.
 *
 * ### What these are, precisely
 *
 * These follow the *shape* of FuzzyWuzzy's `ratio` / `partial_ratio` / `token_set_ratio`, but the
 * base measure is **Levenshtein-normalized** (`1 - distance / maxLength`) rather than
 * `SequenceMatcher`'s matching-blocks ratio. The two agree on identical and wildly different
 * strings and differ modestly in between. Stated plainly because the alternative — calling this
 * "fuzzywuzzy's algorithm" — would invite someone to port a threshold from a Python project and
 * find it doesn't behave the same.
 *
 * Implemented here rather than pulling in `me.xdrop:fuzzywuzzy`: it is under a hundred lines, it
 * keeps `domain` free of a dependency, and the matching thresholds need to be tuned against this
 * repo's fixtures anyway.
 *
 * ### Why three measures instead of one
 *
 * Each fails on a case the others handle, which is why the caller blends them:
 *
 * | Measure | Good at | Blind to |
 * |---|---|---|
 * | [ratio] | Small edits, typos | Word reordering; one title being a subset of another |
 * | [partialRatio] | A short title inside a long one (`"Berserk"` vs `"Berserk: Deluxe Edition"`) | Reordering |
 * | [tokenSetRatio] | Reordering and extra words (`"Boku no Hero"` vs `"Hero Academia, Boku no"`) | Typos inside a token |
 *
 * All three take strings that are **already normalized** — none of them lowercase or strip
 * punctuation. Normalizing once at the call site avoids doing it three times per candidate.
 */
object StringSimilarity {

    /**
     * Levenshtein-normalized similarity, 0.0 to 1.0.
     *
     * Two empty strings are 1.0 (identical), not 0.0 — the alternative makes empty input look
     * maximally *different* from itself, which propagates as a confusing zero through the caller's
     * weighted blend.
     */
    fun ratio(a: String, b: String): Float {
        if (a == b) return 1f
        val maxLength = maxOf(a.length, b.length)
        if (maxLength == 0) return 1f
        return 1f - levenshtein(a, b).toFloat() / maxLength
    }

    /**
     * The best [ratio] between the shorter string and any equal-length window of the longer one.
     *
     * This is what catches a title that is a *subtitle* of another — `"berserk"` inside
     * `"berserk deluxe edition"` scores 1.0 here and about 0.32 under [ratio], because plain edit
     * distance charges for every character of the extra words.
     */
    fun partialRatio(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return if (a == b) 1f else 0f
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        if (shorter.length == longer.length) return ratio(shorter, longer)

        var best = 0f
        for (start in 0..(longer.length - shorter.length)) {
            val window = longer.substring(start, start + shorter.length)
            best = maxOf(best, ratio(shorter, window))
            if (best == 1f) return 1f
        }
        return best
    }

    /**
     * Compares the two strings as *sets of words*, so order and duplication stop mattering.
     *
     * Following FuzzyWuzzy's construction: build the sorted intersection of the token sets and the
     * two sorted remainders, then take the best ratio among intersection-vs-each-full-string and
     * the two full strings against each other. The intersection comparisons are what make a title
     * that contains all of another's words score highly regardless of arrangement.
     */
    fun tokenSetRatio(a: String, b: String): Float {
        val tokensA = a.split(WHITESPACE).filter { it.isNotEmpty() }.toSortedSet()
        val tokensB = b.split(WHITESPACE).filter { it.isNotEmpty() }.toSortedSet()
        if (tokensA.isEmpty() && tokensB.isEmpty()) return 1f
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f

        val intersection = tokensA.intersect(tokensB).joinToString(" ")
        val restOfA = (tokensA - tokensB).joinToString(" ")
        val restOfB = (tokensB - tokensA).joinToString(" ")

        val combinedA = "$intersection $restOfA".trim()
        val combinedB = "$intersection $restOfB".trim()

        return maxOf(
            ratio(intersection, combinedA),
            ratio(intersection, combinedB),
            ratio(combinedA, combinedB),
        )
    }

    /**
     * Levenshtein edit distance.
     *
     * Two rows rather than a full matrix: the recurrence only ever reads the previous row, and a
     * title pair is small enough that the allocation, not the arithmetic, dominates.
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private val WHITESPACE = Regex("""\s+""")
}
