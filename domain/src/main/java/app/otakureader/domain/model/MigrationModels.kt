package app.otakureader.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a migration operation mode.
 */
enum class MigrationMode {
    /** Copy the manga (keep both old and new) */
    COPY,
    /** Move the manga (replace old with new) */
    MOVE
}

/**
 * Represents a candidate manga match from target source for migration.
 */
@Serializable
data class MigrationCandidate(
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val chapterCount: Int = 0,
    /** Similarity score (0.0 to 1.0) based on title matching */
    val similarityScore: Float = 0f
)

/**
 * Represents a migration task for a single manga.
 */
data class MigrationTask(
    val sourceManga: Manga,
    val targetCandidate: MigrationCandidate? = null,
    val mode: MigrationMode = MigrationMode.MOVE,
    val status: MigrationStatus = MigrationStatus.PENDING
)

/**
 * Status of a migration task.
 */
enum class MigrationStatus {
    PENDING,
    SEARCHING,
    AWAITING_CONFIRMATION,
    MIGRATING,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Result of a migration operation.
 */
data class MigrationResult(
    val originalMangaId: Long,
    val newMangaId: Long? = null,
    val chaptersMatched: Int = 0,
    val status: MigrationStatus,
    val error: String? = null
)
