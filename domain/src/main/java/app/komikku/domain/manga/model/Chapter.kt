package app.komikku.domain.manga.model

data class Chapter(
    val id: Long = 0,
    val mangaId: Long,
    val url: String,
    val name: String,
    val dateUpload: Long = 0L,
    val chapterNumber: Float = -1f,
    val scanlator: String = "",
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
)
