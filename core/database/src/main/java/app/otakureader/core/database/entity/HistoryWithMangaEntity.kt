package app.otakureader.core.database.entity

import androidx.room.ColumnInfo

/**
 * Flat projection returned by
 * [app.otakureader.core.database.dao.ReadingHistoryDao.observeHistoryWithMangaInfo].
 *
 * The query joins `chapters`, `reading_history`, and `manga` in a single SQL statement so that the
 * History screen can display the manga cover and title without issuing additional look-ups.
 */
data class HistoryWithMangaEntity(
    @ColumnInfo(name = "id")             val chapterId: Long,
    @ColumnInfo(name = "mangaId")        val mangaId: Long,
    @ColumnInfo(name = "url")            val url: String,
    @ColumnInfo(name = "name")           val name: String,
    @ColumnInfo(name = "scanlator")      val scanlator: String?,
    @ColumnInfo(name = "read")           val read: Boolean,
    @ColumnInfo(name = "bookmark")       val bookmark: Boolean,
    @ColumnInfo(name = "lastPageRead")   val lastPageRead: Int,
    @ColumnInfo(name = "chapterNumber")  val chapterNumber: Float,
    @ColumnInfo(name = "dateFetch")      val dateFetch: Long,
    @ColumnInfo(name = "dateUpload")     val dateUpload: Long,
    @ColumnInfo(name = "read_at")        val readAt: Long,
    @ColumnInfo(name = "read_duration_ms") val readDurationMs: Long,
    @ColumnInfo(name = "manga_title")    val mangaTitle: String?,
    @ColumnInfo(name = "manga_thumbnail") val mangaThumbnailUrl: String?
)
