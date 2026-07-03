package app.otakureader.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room join result that pairs an [UpdateErrorEntity] with its parent [MangaEntity].
 * Used by [app.otakureader.core.database.dao.UpdateErrorDao.observeErrors].
 */
data class UpdateErrorWithMangaEntity(
    @Embedded val error: UpdateErrorEntity,
    @Relation(
        parentColumn = "mangaId",
        entityColumn = "id"
    )
    val manga: MangaEntity
)
