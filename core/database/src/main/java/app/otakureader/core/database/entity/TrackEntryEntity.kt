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
 * forever, and an orphan here is not inert: it keeps a tracker linked to a manga that no longer
 * exists, and re-adding the same manga reuses the stale row along with its old `remote_id`. See
 * #1248.
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
