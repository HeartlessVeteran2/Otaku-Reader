package app.otakureader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
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
        Index(value = ["mangaId", "serviceId"], unique = true)
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Local manga id. */
    val mangaId: Long,
    /** Numeric id from [app.otakureader.domain.model.TrackService]. */
    val serviceId: Int,
    /** Entry id on the remote service. */
    val remoteId: Long,
    val title: String,
    val lastChapterRead: Float = 0f,
    val totalChapters: Int = 0,
    /** Name of [app.otakureader.domain.model.TrackStatus]. */
    val status: String = "READING",
    val score: Float = 0f,
    val remoteUrl: String = ""
)
