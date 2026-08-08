package app.otakureader.data.metadata

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject

/**
 * The metadata half of AniList's GraphQL endpoint.
 *
 * A separate Retrofit interface from `AniListApi` even though both POST to `/graphql`, because the
 * response shapes have nothing in common and sharing one would mean a union type with every field
 * nullable — which is how a missing field becomes indistinguishable from a field that failed to
 * parse. It is built on the same Retrofit instance, so it inherits the rate-limit interceptor and
 * certificate pinning without restating either.
 */
interface AniListMetadataService {
    @POST("graphql")
    suspend fun query(@Body body: MetadataGraphQlQuery): MetadataResponse

    /**
     * Same endpoint, different response type.
     *
     * Retrofit picks the deserializer from the declared return type, so a search cannot reuse
     * [query] without making `MetadataResponse` cover both shapes — every field nullable, and a
     * missing `Media` indistinguishable from a missing `Page`.
     */
    @POST("graphql")
    suspend fun search(@Body body: MetadataGraphQlQuery): SearchResponse
}

@kotlinx.serialization.Serializable
data class MetadataGraphQlQuery(
    val query: String,
    val variables: JsonObject = JsonObject(emptyMap()),
)

/**
 * Wraps [AniListMetadataService] so the repository states *what* it wants rather than how the
 * query is spelled.
 *
 * Worth the indirection for one reason: the query text and the variable typing are the two things
 * AniList rejects a document over — `mediaId` as a quoted string was exactly the defect #1232
 * fixed — and keeping both in one place means a test can assert them without a repository test
 * having to know GraphQL.
 */
class AniListMetadataApi @Inject constructor(
    private val service: AniListMetadataService,
) {
    suspend fun fetch(anilistId: Long): MetadataResponse =
        service.query(
            MetadataGraphQlQuery(
                query = METADATA_QUERY,
                // A number, not a string: the query declares `$id: Int`, and AniList validates
                // variables against the declared types before running anything.
                variables = buildJsonObject { put("id", anilistId) },
            )
        )

    /**
     * Search AniList for manga whose title resembles [title].
     *
     * [perPage] is capped rather than taken on trust: AniList's own maximum is 50, and the matcher
     * gains nothing from a long tail — it scores every candidate, so a hundred near-misses only
     * costs time and bandwidth to reject.
     */
    suspend fun searchMedia(title: String, perPage: Int = DEFAULT_PER_PAGE): SearchResponse =
        service.search(
            MetadataGraphQlQuery(
                query = SEARCH_QUERY,
                variables = buildJsonObject {
                    put("search", title)
                    put("perPage", perPage.coerceIn(1, MAX_PER_PAGE))
                },
            )
        )

    companion object {
        /** Enough for the right entry to be present; short enough that scoring stays cheap. */
        const val DEFAULT_PER_PAGE = 10

        /** AniList rejects anything above this. */
        const val MAX_PER_PAGE = 50
    }
}
