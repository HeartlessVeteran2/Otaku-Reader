package app.otakureader.domain.model

import kotlinx.serialization.Serializable

/**
 * A named search query the user has bookmarked for a specific source.
 *
 * Stored as a JSON-serialized list in [app.otakureader.core.preferences.GeneralPreferences] so no
 * database migration is needed. The [id] is a random UUID string that serves as a stable key for
 * Compose list diffing and deletion.
 */
@Serializable
data class SavedSourceSearch(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val query: String,
    val sourceId: Long,
    val sourceName: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Manual display order (ascending). Reorderable from the Browse screen (Komikku parity). */
    val order: Int = 0,
)
