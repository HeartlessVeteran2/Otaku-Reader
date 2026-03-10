package app.otakureader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mangaId"]),
        Index(value = ["mangaId", "url"], unique = true),
        Index(value = ["read"]),
        Index(value = ["bookmark"]),
        Index(value = ["dateFetch"])
    ]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mangaId: Long,
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
    val lastModified: Long = 0
)
