package app.otakureader.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room join result that pairs a [ChapterEntity] with its parent [MangaEntity].
 * Used by [app.otakureader.core.database.dao.ChapterDao.getRecentUpdates].
 */
data class ChapterWithMangaEntity(
    @Embedded val chapter: ChapterEntity,
    @Relation(
        parentColumn = "mangaId",
        entityColumn = "id"
    )
    val manga: MangaEntity
)
