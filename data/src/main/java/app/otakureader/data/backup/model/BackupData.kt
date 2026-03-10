package app.otakureader.data.backup.model

import kotlinx.serialization.Serializable

/**
 * Root backup data structure containing all app data that can be backed up and restored.
 * Uses kotlinx.serialization for JSON export/import.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val manga: List<BackupManga> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val preferences: BackupPreferences? = null
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Backup representation of a manga with its chapters and category associations.
 */
@Serializable
data class BackupManga(
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: Int = 0,
    val favorite: Boolean = false,
    val lastUpdate: Long = 0,
    val initialized: Boolean = false,
    val viewerFlags: Int = 0,
    val chapterFlags: Int = 0,
    val coverLastModified: Long = 0,
    val dateAdded: Long = 0,
    val chapters: List<BackupChapter> = emptyList(),
    val categoryIds: List<Long> = emptyList(),
    val notes: String? = null,
    val readerBackgroundColor: Long? = null
)

/**
 * Backup representation of a chapter with its reading history.
 */
@Serializable
data class BackupChapter(
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val chapterNumber: Float = -1f,
    val sourceOrder: Int = 0,
    val dateFetch: Long = 0,
    val dateUpload: Long = 0,
    val lastModified: Long = 0,
    val readingHistory: BackupReadingHistory? = null
)

/**
 * Backup representation of reading history for a chapter.
 */
@Serializable
data class BackupReadingHistory(
    val readAt: Long = 0L,
    val readDurationMs: Long = 0L
)

/**
 * Backup representation of a category.
 */
@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val order: Int = 0,
    val flags: Int = 0
)

/**
 * Backup representation of user preferences.
 */
@Serializable
data class BackupPreferences(
    val themeMode: Int = 0,
    val useDynamicColor: Boolean = true,
    val locale: String = "",
    val readerMode: Int = 0,
    val keepScreenOn: Boolean = true,
    val volumeKeysEnabled: Boolean = false,
    val volumeKeysInverted: Boolean = false,
    val libraryGridSize: Int = 3,
    val showBadges: Boolean = true,
    val updateCheckInterval: Int = 12,
    val notificationsEnabled: Boolean = true
)
