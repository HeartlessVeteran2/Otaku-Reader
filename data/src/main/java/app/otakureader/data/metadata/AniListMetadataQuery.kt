package app.otakureader.data.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The AniList query behind the details screen, and the shapes it comes back as.
 *
 * Kept apart from `TrackingApis.kt` deliberately. That file describes what the *tracker* needs —
 * ids, status, progress, score — and it is small on purpose because every field there is written
 * back to AniList. This is read-only presentation data, several times larger, and mixing them
 * would make it unclear which fields a mutation is allowed to touch.
 *
 * ### One query, not two
 *
 * The plan called for splitting this into a blocking primary query and a deferred secondary one so
 * the screen paints sooner. That split is a real optimisation, but it belongs after there is a
 * screen to measure: the fields are all in one `Media` object, so two queries means two round trips
 * to the same endpoint, against a rate limit this app now has to wait out (#1236). Left as one
 * query with a note rather than built on a guess about where the time goes.
 */
internal const val METADATA_QUERY = """
    query (${'$'}id: Int) {
      Media(id: ${'$'}id, type: MANGA) {
        id
        description(asHtml: false)
        bannerImage
        coverImage { extraLarge large color }
        genres
        tags { name rank isMediaSpoiler isGeneralSpoiler }
        averageScore
        popularity
        favourites
        format
        countryOfOrigin
        status
        chapters
        startDate { year month day }
        endDate { year month day }
        synonyms
        title { romaji english native userPreferred }
      }
    }
"""

@Serializable
data class MetadataResponse(
    val data: MetadataData? = null,
    val errors: List<MetadataError> = emptyList(),
)

@Serializable
data class MetadataError(val message: String = "")

@Serializable
data class MetadataData(
    @SerialName("Media") val media: MetadataMedia? = null,
)

@Serializable
data class MetadataMedia(
    val id: Long = 0,
    val description: String? = null,
    val bannerImage: String? = null,
    val coverImage: MetadataCoverImage? = null,
    val genres: List<String> = emptyList(),
    val tags: List<MetadataTag> = emptyList(),
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val format: String? = null,
    val countryOfOrigin: String? = null,
    val status: String? = null,
    val chapters: Int? = null,
    val startDate: MetadataDate? = null,
    val endDate: MetadataDate? = null,
    val synonyms: List<String> = emptyList(),
    val title: MetadataTitle? = null,
)

@Serializable
data class MetadataCoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    /** AniList's own dominant-colour extraction, as `#rrggbb`. */
    val color: String? = null,
)

@Serializable
data class MetadataTag(
    val name: String = "",
    val rank: Int = 0,
    /**
     * Two separate spoiler flags, and both matter.
     *
     * `isGeneralSpoiler` marks a tag that spoils the work for anyone; `isMediaSpoiler` marks one
     * that spoils *this* entry specifically. A tag with either set is dropped before it reaches the
     * domain model — checking only one would still put "Major Character Death" on a details page.
     */
    val isMediaSpoiler: Boolean = false,
    val isGeneralSpoiler: Boolean = false,
)

@Serializable
data class MetadataDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
data class MetadataTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
    val userPreferred: String? = null,
)
