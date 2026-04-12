package app.otakureader.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Lightweight sync snapshot for cross-device synchronization.
 *
 * This is lighter weight than [app.otakureader.data.backup.model.BackupData] as it:
 * - Excludes user preferences (device-specific)
 * - Excludes reading history (too large, sync separately if needed)
 * - Focuses on library state: favorites, categories, read progress
 * - Includes device and version tracking for conflict resolution
 *
 * Size optimization is critical as this will be uploaded/downloaded frequently.
 */
@Immutable
@Serializable
data class SyncSnapshot(
    /** Version of the sync snapshot format. */
    val version: Int = CURRENT_VERSION,

    /** Timestamp when this snapshot was created (ms since epoch). */
    val createdAt: Long = System.currentTimeMillis(),

    /** Unique identifier for the device that created this snapshot. */
    val deviceId: String,

    /** Optional device name for display purposes. */
    val deviceName: String? = null,

    /** List of manga in the library with sync-relevant data. */
    val manga: List<SyncManga> = emptyList(),

    /** List of categories. */
    val categories: List<SyncCategory> = emptyList(),

    /** Metadata for conflict resolution and versioning. */
    val metadata: SyncMetadata = SyncMetadata()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Sync representation of a manga.
 * Contains only essential fields needed for sync.
 */
@Immutable
@Serializable
data class SyncManga(
    /** Unique identifier combining source and URL. */
    val sourceId: Long,
    val url: String,

    /** Display information. */
    val title: String,
    val thumbnailUrl: String? = null,

    /** Library state. */
    val favorite: Boolean = false,
    val categoryIds: List<Long> = emptyList(),

    /** Last modification timestamp for this manga (ms since epoch). */
    val lastModified: Long = System.currentTimeMillis(),

    /** Optional user notes. */
    val notes: String? = null,

    /** Chapter read progress (lightweight). */
    val chapters: List<SyncChapter> = emptyList()
)

/**
 * Sync representation of a chapter.
 * Only includes read state, not full chapter metadata.
 */
@Immutable
@Serializable
data class SyncChapter(
    /** Chapter identifier (URL is the stable key). */
    val url: String,

    /** Read state. */
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,

    /** Last modification timestamp for this chapter (ms since epoch). */
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Sync representation of a category.
 */
@Immutable
@Serializable
data class SyncCategory(
    /** Category ID (stable across devices). */
    val id: Long,

    /** Category name. */
    val name: String,

    /** Display order. */
    val order: Int = 0,

    /** Last modification timestamp (ms since epoch). */
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Metadata for sync operations and conflict resolution.
 */
@Immutable
@Serializable
data class SyncMetadata(
    /** Incremental version number for this sync state. */
    val syncVersion: Long = 0,

    /** Previous sync version this snapshot is based on. */
    val baseSyncVersion: Long? = null,

    /** Hash of the previous snapshot for integrity checking. */
    val previousSnapshotHash: String? = null,

    /** Application version that created this snapshot. */
    val appVersion: String? = null
)

/**
 * Result of a sync operation.
 */
@Immutable
@Serializable
data class SyncResult(
    /** Whether sync completed successfully. */
    val success: Boolean,

    /** Timestamp when sync completed. */
    val timestamp: Long = System.currentTimeMillis(),

    /** Number of manga added/updated/deleted. */
    val mangaAdded: Int = 0,
    val mangaUpdated: Int = 0,
    val mangaDeleted: Int = 0,

    /** Number of chapters updated. */
    val chaptersUpdated: Int = 0,

    /** Number of categories added/updated/deleted. */
    val categoriesAdded: Int = 0,
    val categoriesUpdated: Int = 0,
    val categoriesDeleted: Int = 0,

    /** Number of conflicts encountered. */
    val conflicts: Int = 0,

    /** Human-readable message. */
    val message: String? = null,

    /** Error details if sync failed. */
    val error: String? = null
) {
    /** Total number of changes applied. */
    val totalChanges: Int
        get() = mangaAdded + mangaUpdated + mangaDeleted +
                chaptersUpdated +
                categoriesAdded + categoriesUpdated + categoriesDeleted
}
