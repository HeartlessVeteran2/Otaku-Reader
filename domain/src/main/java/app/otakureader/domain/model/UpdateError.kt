package app.otakureader.domain.model

/** A manga's most recent library-update failure, shown on the Update Errors screen. */
data class UpdateError(
    val mangaId: Long,
    val mangaTitle: String,
    val thumbnailUrl: String?,
    val errorMessage: String,
    val timestamp: Long,
)
