package app.otakureader.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Chapter(
    val id: Long,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val chapterNumber: Float = -1f,
    val dateUpload: Long = 0,
    val dateFetch: Long = 0
)
