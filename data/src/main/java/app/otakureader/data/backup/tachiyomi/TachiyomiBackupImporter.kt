package app.otakureader.data.backup.tachiyomi

import androidx.room.withTransaction
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.TrackEntryDao
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.TrackEntryEntity
import app.otakureader.domain.backup.TachiyomiBackupImporter as TachiyomiBackupImporterInterface
import app.otakureader.domain.model.ImportResult as DomainImportResult
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.TachiyomiBackupPreview
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Imports Mihon/Tachiyomi backups (protobuf `.tachibk` / `.proto.gz` or legacy JSON) into
 * Otaku-Reader's database. Format detection and decoding live in [TachiyomiBackupParser]; this
 * class maps the parsed model into Room entities.
 */
class TachiyomiBackupImporter @Inject constructor(
    private val database: OtakuReaderDatabase,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val trackEntryDao: TrackEntryDao,
    private val parser: TachiyomiBackupParser,
) : TachiyomiBackupImporterInterface {

    override suspend fun preview(data: ByteArray): TachiyomiBackupPreview {
        val backup = parser.parse(data)
        return TachiyomiBackupPreview(
            mangaCount = backup.manga.size,
            categoryCount = backup.categories.size,
            chapterCount = backup.chapterCount,
            trackingCount = backup.trackingCount,
        )
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override suspend fun importBackup(
        data: ByteArray,
        overwriteExisting: Boolean,
        onProgress: (current: Int, total: Int) -> Unit,
    ): DomainImportResult {
        val backup = parser.parse(data)

        var mangaImported = 0
        var chaptersImported = 0
        var categoriesImported = 0
        var trackingImported = 0
        var skipped = 0
        val totalManga = backup.manga.size

        database.withTransaction {
            // Categories first, so manga can be linked. Key by the backup's category `order`,
            // which is how manga reference their categories. Reuse an existing category with the
            // same name instead of inserting a duplicate on repeated imports.
            val existingByName = categoryDao.getCategories().first().associateBy { it.name }
            val orderToCategoryId = mutableMapOf<Long, Long>()
            backup.categories.forEach { category ->
                val existingCategory = existingByName[category.name]
                val categoryId = if (existingCategory != null) {
                    existingCategory.id
                } else {
                    categoryDao.insert(
                        CategoryEntity(
                            id = 0,
                            name = category.name,
                            order = category.order,
                            flags = category.flags.toInt(),
                        )
                    ).also { categoriesImported++ }
                }
                orderToCategoryId[category.order.toLong()] = categoryId
            }

            backup.manga.forEachIndexed { index, manga ->
                val existing = mangaDao.getMangaBySourceAndUrl(manga.source, manga.url)

                if (existing != null && !overwriteExisting) {
                    skipped++
                    onProgress(index + 1, totalManga)
                    return@forEachIndexed
                }

                val entity = MangaEntity(
                    id = existing?.id ?: 0,
                    sourceId = manga.source,
                    url = manga.url,
                    title = manga.title,
                    artist = manga.artist,
                    author = manga.author,
                    description = manga.description,
                    genre = manga.genre.joinToString(","),
                    status = tachiyomiStatusToDomain(manga.status).ordinal,
                    thumbnailUrl = manga.thumbnailUrl,
                    favorite = manga.favorite,
                    lastUpdate = manga.lastUpdate,
                    initialized = existing?.initialized ?: false,
                    viewerFlags = manga.viewerFlags,
                    chapterFlags = manga.chapterFlags,
                    dateAdded = manga.dateAdded,
                )

                val mangaId: Long
                if (existing != null) {
                    mangaDao.update(entity)
                    mangaId = existing.id
                } else {
                    // insert() returns the autogenerated row id; entity.id stays 0 for new rows.
                    mangaId = mangaDao.insertOrGetExisting(entity)
                }
                mangaImported++

                chaptersImported += restoreChapters(mangaId, manga.chapters)
                restoreCategoryLinks(mangaId, manga.categoryOrders, orderToCategoryId, overwriteExisting)
                trackingImported += restoreTracking(mangaId, manga.tracking)

                onProgress(index + 1, totalManga)
            }
        }

        return DomainImportResult(
            mangaImported = mangaImported,
            chaptersImported = chaptersImported,
            categoriesImported = categoriesImported,
            skipped = skipped,
            totalManga = totalManga,
            totalChapters = backup.chapterCount,
            trackingImported = trackingImported,
        )
    }

    private suspend fun restoreChapters(mangaId: Long, chapters: List<ParsedChapter>): Int {
        if (chapters.isEmpty()) return 0
        // Mutable, and updated as rows are inserted, so a URL repeated later in the same backup
        // updates the row this loop just created instead of being treated as new again. The
        // snapshot alone is stale the moment the first insert lands: the duplicate would take the
        // upsert path, which by design refreshes only source metadata, and the backup's own `read`,
        // `lastPageRead`, `sourceOrder` and `dateFetch` would be silently dropped — while `inserted`
        // counted the same chapter twice. BackupRestorer already does this for the same reason.
        val existingByUrl = chapterDao.getChaptersByMangaId(mangaId).first()
            .associateBy { it.url }
            .toMutableMap()
        var inserted = 0
        chapters.forEach { chapter ->
            val existing = existingByUrl[chapter.url]
            val entity = ChapterEntity(
                id = existing?.id ?: 0,
                mangaId = mangaId,
                url = chapter.url,
                name = chapter.name,
                read = chapter.read,
                lastPageRead = chapter.lastPageRead,
                chapterNumber = chapter.chapterNumber ?: -1f,
                sourceOrder = chapter.sourceOrder,
                dateFetch = chapter.dateFetch,
                dateUpload = chapter.dateUpload,
                lastModified = 0,
            )
            if (existing != null) {
                chapterDao.update(entity)
            } else {
                val newId = chapterDao.upsert(entity)
                existingByUrl[chapter.url] = entity.copy(id = newId)
                inserted++
            }
        }
        return inserted
    }

    private suspend fun restoreCategoryLinks(
        mangaId: Long,
        categoryOrders: List<Long>,
        orderToCategoryId: Map<Long, Long>,
        overwriteExisting: Boolean,
    ) {
        if (categoryOrders.isEmpty()) return
        if (overwriteExisting) {
            categoryDao.deleteMangaCategoriesForManga(mangaId)
        }
        categoryOrders.forEach { order ->
            val categoryId = orderToCategoryId[order] ?: return@forEach
            categoryDao.insertMangaCategory(MangaCategoryEntity(mangaId = mangaId, categoryId = categoryId))
        }
    }

    private suspend fun restoreTracking(mangaId: Long, tracking: List<ParsedTracking>): Int {
        var inserted = 0
        tracking.forEach { track ->
            // Guard against malformed/ambiguous tracker records so we never write junk links.
            if (track.trackerId <= 0 || track.remoteId <= 0L) return@forEach
            trackEntryDao.upsert(
                TrackEntryEntity(
                    id = 0,
                    mangaId = mangaId,
                    trackerId = track.trackerId,
                    remoteId = track.remoteId,
                    remoteUrl = track.url,
                    title = track.title ?: "",
                    status = track.status,
                    lastChapterRead = track.lastChapterRead,
                    totalChapters = track.totalChapters,
                    score = track.score,
                    startDate = track.startDate,
                    finishDate = track.finishDate,
                )
            )
            inserted++
        }
        return inserted
    }

    private fun tachiyomiStatusToDomain(status: Int): MangaStatus {
        return when (status) {
            1 -> MangaStatus.ONGOING
            2 -> MangaStatus.COMPLETED
            3 -> MangaStatus.LICENSED
            4 -> MangaStatus.PUBLISHING_FINISHED
            5 -> MangaStatus.CANCELLED
            6 -> MangaStatus.ON_HIATUS
            else -> MangaStatus.UNKNOWN
        }
    }
}
