package app.otakureader.data.tracking.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

// MyAnimeList API Models

@Serializable
data class MalOAuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class MalMangaSearchResponse(
    val data: List<MalMangaNode>
)

@Serializable
data class MalMangaNode(
    val node: MalManga
)

@Serializable
data class MalManga(
    val id: Long,
    val title: String,
    @SerialName("main_picture") val mainPicture: MalPicture? = null,
    @SerialName("num_chapters") val numChapters: Int? = null
)

@Serializable
data class MalPicture(
    val medium: String,
    val large: String
)

@Serializable
data class MalMangaListStatus(
    val status: String? = null,
    val score: Int? = null,
    @SerialName("num_chapters_read") val numChaptersRead: Int? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("finish_date") val finishDate: String? = null
)

// AniList API Models

@Serializable
data class AnilistOAuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class AnilistGraphQLRequest(
    val query: String,
    @Contextual val variables: Map<String, @Contextual Any> = emptyMap()
)

@Serializable
data class AnilistGraphQLResponse<T>(
    @Contextual val data: T? = null,
    val errors: List<AnilistError>? = null
)

@Serializable
data class AnilistError(
    val message: String
)

@Serializable
data class AnilistSearchData(
    @SerialName("Page") val page: AnilistPage
)

@Serializable
data class AnilistPage(
    val media: List<AnilistMedia>
)

@Serializable
data class AnilistMedia(
    val id: Long,
    val title: AnilistTitle,
    val chapters: Int? = null,
    @SerialName("coverImage") val coverImage: AnilistCoverImage? = null
)

@Serializable
data class AnilistTitle(
    val romaji: String,
    val english: String? = null
)

@Serializable
data class AnilistCoverImage(
    val large: String
)

@Serializable
data class AnilistMediaListData(
    @SerialName("SaveMediaListEntry") val saveMediaListEntry: AnilistMediaList? = null,
    @SerialName("MediaList") val mediaList: AnilistMediaList? = null
)

@Serializable
data class AnilistMediaList(
    val id: Long,
    val status: String? = null,
    val score: Float? = null,
    val progress: Int? = null,
    @SerialName("startedAt") val startedAt: AnilistFuzzyDate? = null,
    @SerialName("completedAt") val completedAt: AnilistFuzzyDate? = null
)

@Serializable
data class AnilistFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class AnilistViewerData(
    @SerialName("Viewer") val viewer: AnilistViewer
)

@Serializable
data class AnilistViewer(
    val id: Long,
    val name: String
)
