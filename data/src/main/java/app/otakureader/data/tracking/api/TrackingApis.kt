package app.otakureader.data.tracking.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ─────────────────────────────────────────────────────────────────────────────
// MyAnimeList
// ─────────────────────────────────────────────────────────────────────────────

interface MyAnimeListOAuthApi {
    @FormUrlEncoded
    @POST("token")
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("redirect_uri") redirectUri: String
    ): MalTokenResponse

    @FormUrlEncoded
    @POST("token")
    suspend fun refreshAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): MalTokenResponse
}

interface MyAnimeListApi {
    @GET("manga")
    suspend fun searchManga(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("fields") fields: String = "id,title,main_picture,num_chapters"
    ): MalSearchResponse

    @GET("manga/{id}")
    suspend fun getManga(
        @Path("id") id: Long,
        @Query("fields") fields: String = "id,title,main_picture,num_chapters,my_list_status"
    ): MalManga

    @PATCH("manga/{id}/my_list_status")
    @FormUrlEncoded
    suspend fun updateListStatus(
        @Path("id") id: Long,
        @Field("status") status: String,
        @Field("num_chapters_read") chaptersRead: Int,
        @Field("score") score: Int
    ): MalListStatus
}

@Serializable
data class MalTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class MalSearchResponse(
    val data: List<MalSearchItem> = emptyList()
)

@Serializable
data class MalSearchItem(
    val node: MalManga
)

@Serializable
data class MalManga(
    val id: Long,
    val title: String,
    @SerialName("main_picture") val mainPicture: MalPicture? = null,
    @SerialName("num_chapters") val numChapters: Int = 0,
    @SerialName("my_list_status") val listStatus: MalListStatus? = null
)

@Serializable
data class MalPicture(
    val medium: String = "",
    val large: String = ""
)

@Serializable
data class MalListStatus(
    val status: String = "",
    @SerialName("num_chapters_read") val numChaptersRead: Int = 0,
    val score: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// AniList
// ─────────────────────────────────────────────────────────────────────────────

interface AniListApi {
    @POST("graphql")
    suspend fun query(@Body body: AniListGraphQlQuery): AniListResponse
}

@Serializable
data class AniListGraphQlQuery(
    val query: String,
    val variables: Map<String, String> = emptyMap()
)

@Serializable
data class AniListResponse(
    val data: AniListData? = null
)

@Serializable
data class AniListData(
    @SerialName("Media") val media: AniListMedia? = null,
    @SerialName("Page") val page: AniListPage? = null,
    @SerialName("SaveMediaListEntry") val savedEntry: AniListMediaList? = null
)

@Serializable
data class AniListPage(
    val media: List<AniListMedia> = emptyList()
)

@Serializable
data class AniListMedia(
    val id: Long = 0,
    val title: AniListTitle? = null,
    val chapters: Int? = null,
    @SerialName("coverImage") val coverImage: AniListCoverImage? = null,
    @SerialName("mediaListEntry") val mediaListEntry: AniListMediaList? = null
)

@Serializable
data class AniListTitle(
    val romaji: String = "",
    val english: String? = null
)

@Serializable
data class AniListCoverImage(
    val large: String = ""
)

@Serializable
data class AniListMediaList(
    val id: Long = 0,
    val status: String = "",
    val score: Float = 0f,
    val progress: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Kitsu
// ─────────────────────────────────────────────────────────────────────────────

interface KitsuOAuthApi {
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun getAccessToken(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("grant_type") grantType: String = "password",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String
    ): KitsuTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshAccessToken(
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String
    ): KitsuTokenResponse
}

interface KitsuApi {
    @GET("manga")
    suspend fun searchManga(
        @Query("filter[text]") query: String,
        @Query("page[limit]") limit: Int = 20,
        @Query("fields[manga]") fields: String = "id,canonicalTitle,chapterCount,posterImage"
    ): KitsuPagedResponse

    @GET("manga/{id}")
    suspend fun getManga(
        @Path("id") id: Long
    ): KitsuSingleResponse

    @GET("library-entries")
    suspend fun findLibraryEntry(
        @Query("filter[manga_id]") mangaId: Long,
        @Query("filter[userId]") userId: Long,
        @Query("include") include: String = "manga"
    ): KitsuPagedResponse

    @POST("library-entries")
    suspend fun createLibraryEntry(
        @Body body: KitsuLibraryEntryRequest
    ): KitsuLibraryEntryResponse

    @PATCH("library-entries/{id}")
    suspend fun updateLibraryEntry(
        @Path("id") id: Long,
        @Body body: KitsuLibraryEntryRequest
    ): KitsuLibraryEntryResponse

    @GET("users")
    suspend fun getCurrentUser(
        @Query("filter[self]") self: Boolean = true,
        @Query("fields[users]") fields: String = "id,name"
    ): KitsuPagedResponse
}

@Serializable
data class KitsuTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class KitsuPagedResponse(
    val data: List<KitsuResource> = emptyList()
)

@Serializable
data class KitsuSingleResponse(
    val data: KitsuResource
)

@Serializable
data class KitsuResource(
    val id: String = "",
    val type: String = "",
    val attributes: KitsuAttributes = KitsuAttributes()
)

@Serializable
data class KitsuAttributes(
    @SerialName("canonicalTitle") val canonicalTitle: String = "",
    @SerialName("chapterCount") val chapterCount: Int? = null,
    @SerialName("posterImage") val posterImage: KitsuPosterImage? = null,
    val status: String? = null,
    @SerialName("progressedChapters") val progressedChapters: Int = 0,
    @SerialName("ratingTwenty") val ratingTwenty: Int? = null
)

@Serializable
data class KitsuPosterImage(
    val small: String = "",
    val medium: String = "",
    val large: String = ""
)

@Serializable
data class KitsuLibraryEntryRequest(
    val data: KitsuLibraryEntryData
)

@Serializable
data class KitsuLibraryEntryData(
    val type: String = "libraryEntries",
    val attributes: KitsuLibraryEntryAttributes,
    val relationships: KitsuLibraryEntryRelationships? = null
)

@Serializable
data class KitsuLibraryEntryAttributes(
    val status: String,
    @SerialName("progressedChapters") val progressedChapters: Int = 0,
    @SerialName("ratingTwenty") val ratingTwenty: Int? = null
)

@Serializable
data class KitsuLibraryEntryRelationships(
    val manga: KitsuRelationshipData? = null,
    val user: KitsuRelationshipData? = null
)

@Serializable
data class KitsuRelationshipData(
    val data: KitsuResourceIdentifier
)

@Serializable
data class KitsuResourceIdentifier(
    val type: String,
    val id: String
)

@Serializable
data class KitsuLibraryEntryResponse(
    val data: KitsuResource
)

// ─────────────────────────────────────────────────────────────────────────────
// MangaUpdates (BakaUpdates)
// ─────────────────────────────────────────────────────────────────────────────

interface MangaUpdatesApi {
    @POST("account/login")
    suspend fun login(
        @Body credentials: MangaUpdatesLoginRequest
    ): MangaUpdatesLoginResponse

    @DELETE("account/logout")
    suspend fun logout()

    @POST("series/search")
    suspend fun searchSeries(
        @Body request: MangaUpdatesSearchRequest
    ): MangaUpdatesSearchResponse

    @GET("series/{id}")
    suspend fun getSeries(
        @Path("id") id: Long
    ): MangaUpdatesSeries

    @GET("lists/series/{id}")
    suspend fun getListEntry(
        @Path("id") id: Long
    ): MangaUpdatesListEntry

    @POST("lists/series")
    suspend fun addToList(
        @Body request: MangaUpdatesListRequest
    ): MangaUpdatesListEntry

    @POST("lists/series/update")
    suspend fun updateListEntry(
        @Body request: MangaUpdatesListRequest
    ): MangaUpdatesListEntry
}

@Serializable
data class MangaUpdatesLoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class MangaUpdatesLoginResponse(
    val status: String = "",
    @SerialName("context") val context: MangaUpdatesLoginContext? = null
)

@Serializable
data class MangaUpdatesLoginContext(
    @SerialName("session_token") val sessionToken: String = "",
    @SerialName("uid") val uid: Long = 0
)

@Serializable
data class MangaUpdatesSearchRequest(
    val search: String,
    val perpage: Int = 20
)

@Serializable
data class MangaUpdatesSearchResponse(
    @SerialName("total_hits") val totalHits: Int = 0,
    val results: List<MangaUpdatesSearchResult> = emptyList()
)

@Serializable
data class MangaUpdatesSearchResult(
    @SerialName("hit_title") val hitTitle: String = "",
    val record: MangaUpdatesSeries
)

@Serializable
data class MangaUpdatesSeries(
    @SerialName("series_id") val seriesId: Long = 0,
    val title: String = "",
    val image: MangaUpdatesImage? = null,
    @SerialName("completed") val completed: Boolean = false,
    @SerialName("latest_chapter") val latestChapter: Int = 0
)

@Serializable
data class MangaUpdatesImage(
    val url: MangaUpdatesImageUrl? = null
)

@Serializable
data class MangaUpdatesImageUrl(
    val original: String = ""
)

@Serializable
data class MangaUpdatesListEntry(
    val series: MangaUpdatesSeriesRef? = null,
    @SerialName("list_id") val listId: Int = 0,
    @SerialName("status") val status: MangaUpdatesReadStatus? = null,
    @SerialName("chapter") val chapter: Int = 0,
    val priority: Int = 0,
    @SerialName("score") val score: Double? = null
)

@Serializable
data class MangaUpdatesSeriesRef(
    @SerialName("series_id") val seriesId: Long = 0,
    val title: String = ""
)

@Serializable
data class MangaUpdatesReadStatus(
    @SerialName("status") val status: String = ""
)

@Serializable
data class MangaUpdatesListRequest(
    val series: MangaUpdatesSeriesRef,
    @SerialName("list_id") val listId: Int,
    @SerialName("chapter") val chapter: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Shikimori
// ─────────────────────────────────────────────────────────────────────────────

interface ShikimoriOAuthApi {
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("redirect_uri") redirectUri: String
    ): ShikimoriTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("redirect_uri") redirectUri: String
    ): ShikimoriTokenResponse
}

interface ShikimoriApi {
    @GET("mangas")
    suspend fun searchManga(
        @Query("search") query: String,
        @Query("limit") limit: Int = 20,
        @Query("order") order: String = "popularity"
    ): List<ShikimoriManga>

    @GET("mangas/{id}")
    suspend fun getManga(
        @Path("id") id: Long
    ): ShikimoriManga

    @GET("users/whoami")
    suspend fun getCurrentUser(): ShikimoriUser

    @GET("v2/user_rates")
    suspend fun getUserRate(
        @Query("user_id") userId: Long,
        @Query("target_id") targetId: Long,
        @Query("target_type") targetType: String = "Manga"
    ): List<ShikimoriUserRate>

    @POST("v2/user_rates")
    suspend fun createUserRate(
        @Body userRate: ShikimoriUserRateRequest
    ): ShikimoriUserRate

    @PATCH("v2/user_rates/{id}")
    suspend fun updateUserRate(
        @Path("id") id: Long,
        @Body userRate: ShikimoriUserRateRequest
    ): ShikimoriUserRate
}

@Serializable
data class ShikimoriTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class ShikimoriManga(
    val id: Long = 0,
    val name: String = "",
    val russian: String = "",
    val image: ShikimoriImage? = null,
    val url: String = "",
    val kind: String = "",
    val status: String = "",
    val chapters: Int = 0,
    @SerialName("user_rate") val userRate: ShikimoriUserRate? = null
)

@Serializable
data class ShikimoriImage(
    val original: String = "",
    val preview: String = ""
)

@Serializable
data class ShikimoriUser(
    val id: Long = 0,
    val nickname: String = ""
)

@Serializable
data class ShikimoriUserRate(
    val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("target_id") val targetId: Long = 0,
    @SerialName("target_type") val targetType: String = "Manga",
    val status: String = "",
    val score: Int = 0,
    val chapters: Int = 0
)

@Serializable
data class ShikimoriUserRateRequest(
    @SerialName("user_rate") val userRate: ShikimoriUserRateBody
)

@Serializable
data class ShikimoriUserRateBody(
    @SerialName("user_id") val userId: Long,
    @SerialName("target_id") val targetId: Long,
    @SerialName("target_type") val targetType: String = "Manga",
    val status: String,
    val score: Int = 0,
    val chapters: Int = 0
)
