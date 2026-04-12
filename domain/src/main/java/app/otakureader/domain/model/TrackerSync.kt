package app.otakureader.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * Represents sync status for tracker 2-way synchronization.
 */
enum class SyncStatus {
    PENDING,        // Changes waiting to be synced
    SYNCING,        // Currently syncing
    SYNCED,         // Successfully synced
    CONFLICT,       // Conflict detected (remote changed too)
    ERROR           // Sync failed
}

/**
 * Tracks reading progress sync state with external trackers.
 */
@Immutable
data class TrackerSyncState(
    val mangaId: Long,
    val trackerId: Int, // AniList, MAL, etc.
    val remoteId: String, // ID on the tracker service
    
    // Local state
    val localLastChapterRead: Float,
    val localTotalChapters: Int,
    val localStatus: MangaStatus,
    val localLastModified: Instant,
    
    // Remote state (last known)
    val remoteLastChapterRead: Float,
    val remoteTotalChapters: Int,
    val remoteStatus: MangaStatus,
    val remoteLastModified: Instant?,
    
    // Sync state
    val syncStatus: SyncStatus,
    val lastSyncAttempt: Instant?,
    val lastSuccessfulSync: Instant?,
    val syncError: String? = null
)

/** L-13: Named constant for the default auto-sync interval (5 minutes in milliseconds). */
const val DEFAULT_AUTO_SYNC_INTERVAL_MS = 300_000L

/**
 * Configuration for 2-way sync behavior.
 */
@Immutable
data class SyncConfiguration(
    val trackerId: Int,
    val enabled: Boolean = true,
    val syncDirection: SyncDirection = SyncDirection.TWO_WAY,
    val conflictResolution: ConflictResolution = ConflictResolution.ASK,
    // L-13: Use named constant instead of a magic number.
    val autoSyncInterval: Long = DEFAULT_AUTO_SYNC_INTERVAL_MS,
    val syncOnChapterRead: Boolean = true,
    val syncOnMarkComplete: Boolean = true
)

enum class SyncDirection {
    LOCAL_TO_REMOTE,  // Only push local changes
    REMOTE_TO_LOCAL,  // Only pull remote changes
    TWO_WAY           // Bidirectional sync
}

enum class ConflictResolution {
    ASK,              // Prompt user
    LOCAL_WINS,       // Local changes take precedence
    REMOTE_WINS,      // Remote changes take precedence
    NEWEST_WINS       // Most recent modification wins
}
