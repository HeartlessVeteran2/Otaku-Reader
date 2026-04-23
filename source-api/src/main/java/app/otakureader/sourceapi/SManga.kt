package app.otakureader.sourceapi

data class SManga(
    val url: String,
    var title: String = "",
    var description: String = "",
    var thumbnailUrl: String? = null,
    var author: String = "",
    var artist: String = "",
    var genres: String = "",
    var status: Int = 0,
    var initialized: Boolean = false,
    var contentRating: Int = 0,
) {
    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        // contentRating constants
        const val CONTENT_SAFE = 0
        const val CONTENT_SUGGESTIVE = 1
        const val CONTENT_EROTICA = 2
        const val CONTENT_PORNOGRAPHIC = 3
    }
}
