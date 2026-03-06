package app.komikku.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity tracking detailed reading history for a chapter.
 * Each entry links to a chapter and records when and for how long it was read.
 */
@Entity(
    tableName = "reading_history",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chapter_id"], unique = true)]
)
data class ReadingHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "chapter_id") val chapterId: Long,
    @ColumnInfo(name = "read_at") val readAt: Long = 0L,
    @ColumnInfo(name = "read_duration_ms") val readDurationMs: Long = 0L
)
