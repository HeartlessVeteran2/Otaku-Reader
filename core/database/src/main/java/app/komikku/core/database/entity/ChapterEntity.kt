package app.komikku.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for chapter data.
 * Foreign key to MangaEntity with cascade delete so chapters are removed when manga is deleted.
 */
@Entity(
    tableName = "chapter",
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
        Index(value = ["manga_id", "url"], unique = true)
    ]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    @ColumnInfo(name = "date_upload") val dateUpload: Long = 0L,
    @ColumnInfo(name = "chapter_number") val chapterNumber: Float = -1f,
    @ColumnInfo(name = "source_order") val sourceOrder: Int = 0,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    @ColumnInfo(name = "last_page_read") val lastPageRead: Int = 0,
    @ColumnInfo(name = "total_page_count") val totalPageCount: Int = 0,
    @ColumnInfo(name = "date_fetch") val dateFetch: Long = 0L
)
