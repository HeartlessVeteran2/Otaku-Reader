package app.komikku.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a chapter of a manga.
 */
@Serializable
data class Chapter(
    val id: Long = 0L,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val dateUpload: Long = 0L,
    val chapterNumber: Float = -1f,
    val sourceOrder: Int = 0,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val totalPageCount: Int = 0,
    val dateFetch: Long = 0L
)
