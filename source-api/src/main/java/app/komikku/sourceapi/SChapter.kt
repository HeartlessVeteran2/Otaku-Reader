package app.komikku.sourceapi

data class SChapter(
    val url: String,
    var name: String = "",
    var dateUpload: Long = 0L,
    var chapterNumber: Float = -1f,
    var scanlator: String = "",
)
