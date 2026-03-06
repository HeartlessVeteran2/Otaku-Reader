package app.komikku.domain.model

import kotlinx.serialization.Serializable

/** User-defined category for organizing the library. */
@Serializable
data class Category(
    val id: Long = 0L,
    val name: String,
    val order: Int = 0,
    val flags: Long = 0L
)

/** Default category constants. */
object DefaultCategory {
    const val ID_DEFAULT = 0L
    const val NAME_DEFAULT = "Default"
}
