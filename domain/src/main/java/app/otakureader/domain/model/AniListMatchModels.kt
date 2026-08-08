package app.otakureader.domain.model

/**
 * A candidate AniList media entry: the titles matching scores against, plus what a human needs to
 * tell two candidates apart.
 *
 * Still not the full metadata model — matching runs before anything is fetched in detail, and the
 * matcher stays a pure function over titles that a test can drive from a fixture table. The three
 * display fields exist for the wrong-match picker, and only because of it: a picker showing
 * "Kaguya-sama wa Kokurasetai" three times over is not a choice anyone can make, and the cover,
 * format and year are what separate a work from its sequel, its colored edition and its spin-off.
 *
 * [MatchAniListMediaUseCase] ignores them, deliberately. A cover cannot make a title match better.
 */
data class AniListMediaCandidate(
    val mediaId: Long,
    val romaji: String = "",
    val english: String? = null,
    val native: String? = null,
    val synonyms: List<String> = emptyList(),
    /** Cover thumbnail for the picker. Null when AniList has no image for the entry. */
    val coverImage: String? = null,
    /** AniList's `format` — MANGA, NOVEL, ONE_SHOT, MANHWA… */
    val format: String? = null,
    /** Publication start year, the single most useful disambiguator between a work and its sequel. */
    val startYear: Int? = null,
) {
    /**
     * The best title to show a human, or null when this entry has no usable name at all.
     *
     * Nullable on purpose. Returning `""` would hand the UI a row with an invisible title and no
     * indication anything was wrong, so the absence is made explicit and the caller has to supply
     * a placeholder — which belongs in a string resource, not in a domain model.
     *
     * Synonyms are tried before giving up: they are real names users search by, and by the time a
     * candidate reaches here they have already been stripped of placeholders like "?" and "??".
     */
    val displayTitle: String?
        get() = english?.takeIf { it.isNotBlank() }
            ?: romaji.takeIf { it.isNotBlank() }
            ?: native?.takeIf { it.isNotBlank() }
            ?: synonyms.firstOrNull { it.isNotBlank() }
}

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
