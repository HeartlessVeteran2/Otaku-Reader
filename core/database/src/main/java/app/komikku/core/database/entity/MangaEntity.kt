package app.komikku.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga")
data class MangaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val author: String = "",
    val artist: String = "",
    val genres: List<String> = emptyList(),
    val status: Int = 0,
    val sourceId: String = "",
    val url: String = "",
    val isFavorite: Boolean = false,
    val lastUpdate: Long = 0L,
    val dateAdded: Long = 0L,
    val unreadCount: Int = 0,
    val downloadedCount: Int = 0,
)
