package app.otakureader.domain.model

/**
 * Represents a manga the user is actively reading, shown in the
 * "Continue Reading" carousel on the Library screen.
 */
data class ContinueReadingItem(
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val thumbnailUrl: String?,
    val chapterName: String,
    val chapterNumber: Float,
    val lastPageRead: Int,
    val readAt: Long
)
