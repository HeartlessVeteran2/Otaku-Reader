package app.otakureader.domain.util

/**
 * Titles that carry no information, and the check for them.
 *
 * Sources and AniList both emit these for an entry whose localized name is missing — `"?"`,
 * `"??"`, `"N/A"`. They matter for two different reasons, which is why the check lives in one
 * place rather than at each site:
 *
 * - **Matching**: two placeholders are a *perfect* string match, so an unfiltered one can beat the
 *   real answer outright.
 * - **Display**: offering `"?"` to the user as an alternative title is noise at best.
 *
 * This exists because the same bug was written twice. The matcher had its own copy of the set and
 * a case-*sensitive* comparison, which was found in review; the metadata mapper was then written
 * with a second copy and the same case-sensitivity, and found in review again. One definition, one
 * comparison rule, so there is no second copy to get wrong.
 */
object PlaceholderTitles {

    /**
     * True when [title] says something. Comparison is case-insensitive: `"n/a"` is as much a
     * placeholder as `"N/A"`, and both turn up in the wild.
     */
    fun isMeaningful(title: String): Boolean {
        val trimmed = title.trim()
        return trimmed.isNotEmpty() && trimmed.uppercase() !in PLACEHOLDERS
    }

    private val PLACEHOLDERS = setOf("?", "??", "???", "-", "--", "N/A", "NA", "NULL", "UNKNOWN")
}
