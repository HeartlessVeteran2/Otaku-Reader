package app.otakureader.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ChapterWithHistoryEntity(
    @Embedded val history: ReadingHistoryEntity,
    @Relation(
        parentColumn = "chapter_id",
        entityColumn = "id"
    )
    val chapter: ChapterEntity
)
