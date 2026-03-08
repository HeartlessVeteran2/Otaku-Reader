package app.otakureader.data.tracking.api

import app.otakureader.data.tracking.model.*
import retrofit2.http.*

/**
 * MyAnimeList API interface
 * API Documentation: https://myanimelist.net/apiconfig/references/api/v2
 */
interface MyAnimeListApi {

    @FormUrlEncoded
    @POST("v1/oauth2/token")
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): MalOAuthResponse

    @FormUrlEncoded
    @POST("v1/oauth2/token")
    suspend fun refreshAccessToken(
        @Field("client_id") clientId: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): MalOAuthResponse

    @GET("v2/manga")
    suspend fun searchManga(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("fields") fields: String = "id,title,main_picture,num_chapters"
    ): MalMangaSearchResponse

    @GET("v2/manga/{manga_id}/my_list_status")
    suspend fun getMangaListStatus(
        @Header("Authorization") authorization: String,
        @Path("manga_id") mangaId: Long
    ): MalMangaListStatus

    @FormUrlEncoded
    @PUT("v2/manga/{manga_id}/my_list_status")
    suspend fun updateMangaListStatus(
        @Header("Authorization") authorization: String,
        @Path("manga_id") mangaId: Long,
        @Field("status") status: String? = null,
        @Field("score") score: Int? = null,
        @Field("num_chapters_read") numChaptersRead: Int? = null,
        @Field("start_date") startDate: String? = null,
        @Field("finish_date") finishDate: String? = null
    ): MalMangaListStatus

    @DELETE("v2/manga/{manga_id}/my_list_status")
    suspend fun deleteMangaListStatus(
        @Header("Authorization") authorization: String,
        @Path("manga_id") mangaId: Long
    )
}

/**
 * AniList API interface (GraphQL)
 * API Documentation: https://anilist.gitbook.io/anilist-apiv2-docs
 */
interface AniListApi {

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): AnilistOAuthResponse

    @POST("graphql")
    suspend fun graphql(
        @Header("Authorization") authorization: String,
        @Body request: AnilistGraphQLRequest
    ): AnilistGraphQLResponse<Map<String, Any>>
}
