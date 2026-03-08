package app.otakureader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mangaId: Long,
    val serviceId: Int,
    val remoteId: Long = 0,
    val title: String = "",
    val lastChapterRead: Float = 0f,
    val totalChapters: Int = 0,
    val score: Float = 0f,
    val status: Int = 0, // TrackStatus ordinal
    val startDate: Long = 0,
    val finishDate: Long = 0,
    val remoteUrl: String = ""
)
