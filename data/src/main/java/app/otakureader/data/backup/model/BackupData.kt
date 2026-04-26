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
    val preferences: BackupPreferences? = null,
    val opdsServers: List<BackupOpdsServer> = emptyList(),
    val feedSources: List<BackupFeedSource> = emptyList(),
    val feedSavedSearches: List<BackupFeedSavedSearch> = emptyList(),
    val trackerSyncStates: List<BackupTrackerSyncState> = emptyList(),
    val syncConfigurations: List<BackupSyncConfiguration> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 2
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
    val readerBackgroundColor: Long? = null,
    val contentRating: Int = 0,
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

/**
 * Backup representation of an OPDS server.
 * Credentials are stored separately in encrypted storage and are not backed up.
 */
@Serializable
data class BackupOpdsServer(
    val id: Long,
    val name: String,
    val url: String
)

/**
 * Backup representation of a feed source configuration.
 */
@Serializable
data class BackupFeedSource(
    val id: Long,
    val sourceId: Long,
    val sourceName: String,
    val isEnabled: Boolean = true,
    val itemCount: Int = 20,
    val order: Int = 0
)

/**
 * Backup representation of a saved search in the feed.
 */
@Serializable
data class BackupFeedSavedSearch(
    val id: Long,
    val sourceId: Long,
    val sourceName: String,
    val query: String,
    val filtersJson: String? = null,
    val order: Int = 0
)

/**
 * Backup representation of tracker sync state for a single manga+tracker pair.
 * [java.time.Instant] fields are stored as epoch milliseconds for serialization compatibility.
 */
@Serializable
data class BackupTrackerSyncState(
    val mangaId: Long,
    val trackerId: Int,
    val remoteId: String,
    val localLastChapterRead: Float,
    val localTotalChapters: Int,
    val localStatus: Int,
    val localLastModifiedEpochMilli: Long,
    val remoteLastChapterRead: Float,
    val remoteTotalChapters: Int,
    val remoteStatus: Int,
    val remoteLastModifiedEpochMilli: Long? = null,
    val syncStatus: Int,
    val lastSyncAttemptEpochMilli: Long? = null,
    val lastSuccessfulSyncEpochMilli: Long? = null,
    val syncError: String? = null
)

/**
 * Backup representation of per-tracker sync configuration.
 */
@Serializable
data class BackupSyncConfiguration(
    val trackerId: Int,
    val enabled: Boolean = true,
    val syncDirection: Int,
    val conflictResolution: Int,
    val autoSyncInterval: Long = 300_000,
    val syncOnChapterRead: Boolean = true,
    val syncOnMarkComplete: Boolean = true
)
