package app.komikku.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for manga data.
 * Indexed on sourceId+url for fast lookups, and favorite for library filtering.
 */
@Entity(
    tableName = "manga",
    indices = [
        Index(value = ["source_id", "url"], unique = true),
        Index(value = ["favorite"])
    ]
)
data class MangaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: String = "",           // pipe-delimited string (|||)
    val status: Int = 0,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @ColumnInfo(name = "cover_last_modified") val coverLastModified: Long = 0L,
    val favorite: Boolean = false,
    @ColumnInfo(name = "date_added") val dateAdded: Long = 0L,
    @ColumnInfo(name = "last_update") val lastUpdate: Long = 0L,
    val tags: String = ""              // pipe-delimited string (|||)
)
