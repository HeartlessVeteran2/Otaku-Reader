package app.otakureader.data.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How many characters and staff to ask for.
 *
 * A carousel nobody scrolls to the end of does not need the whole cast, and AniList weights query
 * complexity by requested page size — this query already asks for a lot of one `Media`.
 *
 * Interpolated with a plain `$` template, unlike the `${'$'}id` in the query: that one has to reach
 * the server as a literal dollar because it is a *GraphQL* variable, while this is a Kotlin value
 * that must be substituted before the query is sent.
 */
private const val PEOPLE_PER_PAGE = 25

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
        characters(perPage: $PEOPLE_PER_PAGE, sort: [ROLE, RELEVANCE]) {
          edges { role node { id name { full } image { large } } }
        }
        staff(perPage: $PEOPLE_PER_PAGE, sort: [RELEVANCE]) {
          edges { role node { id name { full } image { large } } }
        }
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
    val characters: MetadataCharacterConnection? = null,
    val staff: MetadataStaffConnection? = null,
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

/**
 * Title search, used to find which AniList media a source manga corresponds to.
 *
 * ### Why this is not `AniListTracker.search`
 *
 * The tracker already has a search, and it is the wrong shape twice over. It asks only for
 * `title { romaji english }`, while the matcher scores against **every** name a work is known by —
 * dropping `native` and `synonyms` throws away the evidence that resolves a Japanese-titled source
 * against an English AniList entry. And it returns `TrackEntry`, a *tracking* record, so using it
 * here would mean inventing a fake list entry for a manga the user does not track — which is the
 * exact case this exists to serve.
 *
 * ### Titles for the matcher, three more fields for the picker
 *
 * It fetched titles and nothing else until the wrong-match picker existed to consume more, because
 * anything unread would have been fetched, parsed and dropped. `coverImage`, `format` and the start
 * year are here now for exactly one reason: a picker listing "Kaguya-sama wa Kokurasetai" three
 * times over is not a choice a human can make. The matcher still ignores all three.
 */
internal const val SEARCH_QUERY = """
    query (${'$'}search: String, ${'$'}perPage: Int) {
      Page(perPage: ${'$'}perPage) {
        media(search: ${'$'}search, type: MANGA) {
          id
          title { romaji english native }
          synonyms
          coverImage { large }
          format
          startDate { year }
        }
      }
    }
"""

@Serializable
data class SearchResponse(
    val data: SearchData? = null,
    val errors: List<MetadataError> = emptyList(),
)

@Serializable
data class SearchData(
    @SerialName("Page") val page: SearchPage? = null,
)

@Serializable
data class SearchPage(
    val media: List<SearchMedia> = emptyList(),
)

@Serializable
data class SearchMedia(
    val id: Long = 0,
    val title: MetadataTitle? = null,
    val synonyms: List<String> = emptyList(),
    val coverImage: MetadataCoverImage? = null,
    val format: String? = null,
    val startDate: MetadataDate? = null,
)

/**
 * AniList returns people through an edge/node connection: the **node** is the person, the **edge**
 * is their involvement in *this* manga. Role lives on the edge for that reason — the same voice
 * actor is MAIN here and BACKGROUND there — so it cannot be read off the node.
 */
@Serializable
data class MetadataCharacterConnection(
    val edges: List<MetadataPersonEdge> = emptyList(),
)

@Serializable
data class MetadataStaffConnection(
    val edges: List<MetadataPersonEdge> = emptyList(),
)

/**
 * Characters and staff differ in what `role` *means*, not in shape.
 *
 * For a character it is an enum — `MAIN`, `SUPPORTING`, `BACKGROUND`. For staff it is free text
 * AniList stores verbatim, like "Story & Art" or "Original Creator". Both are carried through
 * unchanged and interpreted at render, so the cache never stores display strings: prettifying an
 * enum on the way in would make it indistinguishable from a staff credit that genuinely reads
 * "Main".
 */
@Serializable
data class MetadataPersonEdge(
    val role: String? = null,
    val node: MetadataPersonNode? = null,
)

@Serializable
data class MetadataPersonNode(
    val id: Long = 0,
    val name: MetadataPersonName? = null,
    val image: MetadataPersonImage? = null,
)

@Serializable
data class MetadataPersonName(
    val full: String? = null,
)

@Serializable
data class MetadataPersonImage(
    val large: String? = null,
)
