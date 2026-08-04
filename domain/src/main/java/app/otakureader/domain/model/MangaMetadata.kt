package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/**
 * Rich metadata for a manga, sourced from AniList and cached locally.
 *
 * ### Why this is separate from [Manga]
 *
 * [Manga] is what the *source* said and what the *user* edited — it is the authoritative local
 * record, it backs the library, and the user can override any of it. This is a second opinion from
 * a third party, keyed to the same manga but owned by nobody local. Folding these fields into
 * `Manga` would put AniList in a position to overwrite a title the user deliberately corrected,
 * and would make "refresh metadata" and "re-read the source" the same operation when they are not.
 *
 * The details screen reads them as two flows and composes them; neither can clobber the other.
 *
 * ### Everything here is optional
 *
 * AniList is missing fields for plenty of entries, especially long-tail and non-Japanese works, and
 * a metadata gap must never look like a load failure. A null description means "AniList has no
 * description", which the UI renders by omitting the section — not by showing an error.
 */
@Immutable
data class MangaMetadata(
    /** The local manga this describes. */
    val mangaId: Long,
    /** The AniList media id this was fetched from — the link the match produced. */
    val anilistId: Long,
    val description: String? = null,
    val bannerImage: String? = null,
    val coverImage: String? = null,
    /** AniList's dominant cover colour as a `#RRGGBB` string, for tinting the header. */
    val coverColor: String? = null,
    val genres: List<String> = emptyList(),
    /** Rank-weighted tags, already filtered of spoilers. See [MangaMetadataTag]. */
    val tags: List<MangaMetadataTag> = emptyList(),
    /** Community score out of 100, or null when too few people have rated it. */
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    /** AniList's `format` — MANGA, NOVEL, ONE_SHOT, MANHWA… */
    val format: String? = null,
    /** ISO country of origin: JP, KR, CN, TW. What distinguishes manga from manhwa/manhua. */
    val countryOfOrigin: String? = null,
    val status: String? = null,
    val chapters: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    /** Alternative titles, de-duplicated and stripped of placeholders. */
    val synonyms: List<String> = emptyList(),
    /** When this was fetched, epoch millis — the basis for the TTL. */
    val fetchedAt: Long = 0L,
)

/**
 * An AniList tag with the community's confidence that it applies.
 *
 * The rank is what makes tags more useful than genres: "Isekai (87%)" says something a bare genre
 * list cannot, and it is the signal the recommendation engine is rebuilt around in Stage 6.
 *
 * Spoiler tags are filtered out **before** a tag ever becomes one of these, so no consumer has to
 * remember to check — a flag on the model would eventually be missed by one call site, and the
 * failure mode is spoiling a plot twist on the details screen.
 */
@Immutable
data class MangaMetadataTag(
    val name: String,
    /** 0–100. AniList's own community-voted relevance. */
    val rank: Int,
)
