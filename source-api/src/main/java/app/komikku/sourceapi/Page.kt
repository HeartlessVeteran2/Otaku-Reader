package app.komikku.sourceapi

data class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
)
