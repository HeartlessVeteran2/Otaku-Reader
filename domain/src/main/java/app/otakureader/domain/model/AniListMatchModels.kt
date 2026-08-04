package app.otakureader.domain.model

/**
 * A candidate AniList media entry, reduced to what title matching needs.
 *
 * Deliberately not the full metadata model: matching runs before anything is fetched in detail, so
 * taking only titles keeps the matcher a pure function that a test can drive from a fixture table.
 */
data class AniListMediaCandidate(
    val mediaId: Long,
    val romaji: String = "",
    val english: String? = null,
    val native: String? = null,
    val synonyms: List<String> = emptyList(),
)

/** The outcome of matching, carrying enough for a picker UI to explain itself. */
data class AniListMatch(
    val candidate: AniListMediaCandidate,
    /** 0.0 to 1.0. Clamped for reporting; ranking uses an unclamped value internally. */
    val score: Float,
    /**
     * False when nothing cleared the accept threshold and this is a best guess.
     *
     * The distinction is what keeps a guess out of the manga row as a confirmed `anilistId` while
     * still giving the details screen something to show.
     */
    val confident: Boolean,
)
