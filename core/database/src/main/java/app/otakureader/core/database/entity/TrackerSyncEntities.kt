package app.otakureader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Entity for tracker sync state - manages 2-way sync with external trackers.
 *
 * The foreign key is load-bearing. Without it, deleting a manga left this row behind, and #1244
 * established that such an orphan is actively harmful rather than merely wasted space:
 * `recordLocalChange` still finds it and keeps marking it PENDING, so `syncAllPending` retries a
 * tracker for a manga that no longer exists — failing every pass, with an error that can never
 * clear. Worse, `syncManga` only auto-creates a row when it finds none, so re-adding the same manga
 * reuses the stale row with its old `remoteId` and chapter history, and the `maxOf` high-water mark
 * in `recordLocalChange` then pins the new entry to the old progress permanently.
 *
 * #1244 closed the unlink route to that state; this closes the manga-deletion route. See #1248.
 */
@Entity(
    tableName = "tracker_sync_state",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mangaId", "trackerId"], unique = true),
        Index(value = ["syncStatus"])
    ]
)
data class TrackerSyncStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mangaId: Long,
    val trackerId: Int,
    val remoteId: String,
    
    // Local state
    val localLastChapterRead: Float,
    val localTotalChapters: Int,
    val localStatus: Int, // MangaStatus ordinal
    val localLastModified: Instant,
    
    // Remote state
    val remoteLastChapterRead: Float,
    val remoteTotalChapters: Int,
    val remoteStatus: Int,
    val remoteLastModified: Instant?,
    
    // Sync state
    val syncStatus: Int, // SyncStatus ordinal
    val lastSyncAttempt: Instant?,
    val lastSuccessfulSync: Instant?,
    val syncError: String?
)

@Entity(
    tableName = "sync_configuration",
    indices = [Index(value = ["trackerId"], unique = true)]
)
data class SyncConfigurationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackerId: Int,
    val enabled: Boolean = true,
    val syncDirection: Int, // SyncDirection ordinal
    val conflictResolution: Int, // ConflictResolution ordinal
    val autoSyncInterval: Long = 300_000,
    val syncOnChapterRead: Boolean = true,
    val syncOnMarkComplete: Boolean = true
)
