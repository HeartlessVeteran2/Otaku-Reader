package app.komikku.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapter",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mangaId")],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mangaId: Long,
    val url: String,
    val name: String,
    val dateUpload: Long = 0L,
    val chapterNumber: Float = -1f,
    val scanlator: String = "",
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val sourceOrder: Int = 0,
)
