package app.komikku.sourceapi

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
) {
    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
    }
}
