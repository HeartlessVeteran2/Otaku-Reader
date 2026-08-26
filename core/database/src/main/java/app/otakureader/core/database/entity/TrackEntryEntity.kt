package app.otakureader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A manga's entry on an external tracker.
 *
 * The foreign key matters as much as the columns. Without it, deleting a manga left this row behind
 * forever, and an orphan here is not inert: `syncAllPending` keeps finding it and retrying a
 * tracker for a manga that no longer exists, failing every pass with an error that can never clear.
 * Deleting the parent has to take this row with it. See #1248.
 *
 * Note what an orphan does *not* do, because an earlier version of this comment claimed it and it
 * is wrong: it is not picked up again by a re-added manga. Manga ids are `AUTOINCREMENT`, so a
 * re-add gets a fresh id and can never collide with the deleted one. (Removing a manga from the
 * library is a different thing entirely — that only flips `favorite`, so the row and its id
 * survive and nothing is orphaned.)
 */
@Entity(
    tableName = "track_entries",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["manga_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["manga_id"]),
        Index(value = ["tracker_id"]),
        Index(value = ["manga_id", "tracker_id"], unique = true)
    ]
)
data class TrackEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "manga_id")         val mangaId: Long,
    @ColumnInfo(name = "tracker_id")       val trackerId: Int,
    @ColumnInfo(name = "remote_id")        val remoteId: Long,
    @ColumnInfo(name = "remote_url")       val remoteUrl: String = "",
    @ColumnInfo(name = "title")            val title: String,
    @ColumnInfo(name = "status")           val status: Int,
    @ColumnInfo(name = "last_chapter_read") val lastChapterRead: Float,
    @ColumnInfo(name = "total_chapters")   val totalChapters: Int,
    @ColumnInfo(name = "score")            val score: Float,
    @ColumnInfo(name = "start_date")       val startDate: Long,
    @ColumnInfo(name = "finish_date")      val finishDate: Long,
)
