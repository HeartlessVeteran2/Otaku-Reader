package app.otakureader.core.database.entity

import androidx.room.ColumnInfo

/**
 * Lightweight projection of the most-recently-read chapter, carrying only
 * the IDs and manga title needed by [app.otakureader.core.database.dao.ReadingHistoryDao.observeLastReadWithMangaTitle].
 */
data class LastReadInfo(
    @ColumnInfo(name = "mangaId") val mangaId: Long,
    @ColumnInfo(name = "chapterId") val chapterId: Long,
    @ColumnInfo(name = "mangaTitle") val mangaTitle: String?
)
