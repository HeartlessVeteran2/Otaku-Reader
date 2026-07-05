package app.otakureader.data.repository

import android.content.Context
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaAlternativeSourceDao
import app.otakureader.core.database.dao.MangaCategoryDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.entity.MangaAlternativeSourceEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.domain.model.ContentRating
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveDownloadFolderName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val mangaCategoryDao: MangaCategoryDao,
    private val altSourceDao: MangaAlternativeSourceDao,
    private val downloadRepository: dagger.Lazy<app.otakureader.domain.repository.DownloadRepository>,
    private val sourceRepository: SourceRepository,
) : MangaRepository {

    override fun getLibraryManga(): Flow<List<Manga>> {
        return mangaDao.getFavoriteMangaWithUnreadCount()
            .distinctUntilChanged()
            .map { mangaWithUnreadList ->
                mangaWithUnreadList.map { it.manga.toDomain(it.unreadCount, it.lastRead) }
            }
    }

    override fun searchLibraryManga(query: String): Flow<List<Manga>> {
        if (query.isBlank()) return getLibraryManga()
        // Append * for prefix matching; escape special FTS chars to avoid query parse errors
        val ftsQuery = query.trim().replace("\"", "\"\"") + "*"
        return mangaDao.searchFts(ftsQuery).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMangaById(id: Long): Manga? {
        return mangaDao.getMangaById(id)?.toDomain()
    }

    override suspend fun getMangaBySourceAndUrl(sourceId: Long, url: String): Manga? {
        return mangaDao.getMangaBySourceAndUrl(sourceId, url)?.toDomain()
    }

    override suspend fun getMangaByIds(ids: List<Long>): List<Manga> {
        if (ids.isEmpty()) return emptyList()
        // Chunk to stay within SQLite's 999 bind-parameter limit, then re-order to match `ids`
        val resultMap = ids.chunked(997).flatMap { chunk ->
            mangaDao.getMangaByIds(chunk).map { it.toDomain() }
        }.associateBy { it.id }
        return ids.mapNotNull { resultMap[it] }
    }

    override fun getMangaByIdFlow(id: Long): Flow<Manga?> {
        return combine(
            mangaDao.getMangaByIdFlow(id),
            chapterDao.getUnreadCountByMangaId(id)
        ) { mangaEntity, unreadCount ->
            mangaEntity?.toDomain(unreadCount)
        }
    }

    override suspend fun insertManga(manga: Manga): Long {
        return mangaDao.insert(manga.toEntity())
    }

    override suspend fun updateManga(manga: Manga) {
        mangaDao.update(manga.toEntity())
    }

    override suspend fun deleteManga(id: Long) {
        mangaDao.deleteById(id)
    }

    override suspend fun toggleFavorite(id: Long) {
        val manga = mangaDao.getMangaById(id) ?: return
        mangaDao.updateFavorite(id, !manga.favorite)
    }

    override suspend fun updateAutoDownload(id: Long, autoDownload: Boolean) {
        mangaDao.updateAutoDownload(id, autoDownload)
    }

    override fun isFavorite(id: Long): Flow<Boolean> {
        return mangaDao.isFavorite(id)
    }

    override suspend fun updateMangaNote(id: Long, notes: String?) {
        mangaDao.updateNote(id, notes)
    }

    override suspend fun updateNotifyNewChapters(id: Long, notify: Boolean) {
        mangaDao.updateNotifyNewChapters(id, notify)
    }

    // Per-manga reader settings (#260)
    override suspend fun updateReaderDirection(id: Long, direction: Int?) {
        mangaDao.updateReaderDirection(id, direction)
    }

    override suspend fun updateReaderMode(id: Long, mode: Int?) {
        mangaDao.updateReaderMode(id, mode)
    }

    override suspend fun updateReaderColorFilter(id: Long, filter: Int?) {
        mangaDao.updateReaderColorFilter(id, filter)
    }

    override suspend fun updateReaderCustomTintColor(id: Long, color: Long?) {
        mangaDao.updateReaderCustomTintColor(id, color)
    }

    override suspend fun updateReaderBackgroundColor(id: Long, color: Long?) {
        mangaDao.updateReaderBackgroundColor(id, color)
    }

    // Page preloading settings (#264)
    override suspend fun updatePreloadPagesBefore(id: Long, count: Int?) {
        mangaDao.updatePreloadPagesBefore(id, count)
    }

    override suspend fun updatePreloadPagesAfter(id: Long, count: Int?) {
        mangaDao.updatePreloadPagesAfter(id, count)
    }

    // Bulk operations
    override suspend fun addToFavorites(id: Long) {
        mangaDao.updateFavorite(id, true)
    }

    override suspend fun removeFromFavorites(id: Long) {
        mangaDao.updateFavorite(id, false)
    }

    override suspend fun addMangaToCategory(mangaId: Long, categoryId: Long) {
        // Implemented via CategoryRepository to avoid circular dependency
        // This is a placeholder - actual implementation uses CategoryDao
    }

    override suspend fun deleteDownloadsForManga(mangaId: Long) {
        // Get manga details to obtain source ID and title
        val manga = mangaDao.getMangaById(mangaId) ?: return

        // Get all chapters for this manga
        val chapters = chapterDao.getChaptersByMangaId(mangaId).first()

        // Delete downloads for each chapter
        val sourceName = sourceRepository.resolveDownloadFolderName(manga.sourceId)
        val mangaTitle = manga.title

        chapters.forEach { chapterEntity ->
            downloadRepository.get().deleteChapterDownload(
                chapterId = chapterEntity.id,
                sourceName = sourceName,
                mangaTitle = mangaTitle,
                chapterTitle = chapterEntity.name
            )
        }
    }

    // Completed series tracking
    override suspend fun markUserCompleted(id: Long, completed: Boolean) {
        mangaDao.updateUserCompleted(id, completed)
    }

    override fun getCompletedManga(): Flow<List<Manga>> {
        return mangaDao.getCompletedManga().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getActiveManga(): Flow<List<Manga>> {
        return mangaDao.getActiveManga().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // Dropped series tracking
    override suspend fun markUserDropped(id: Long, dropped: Boolean) {
        mangaDao.updateUserDropped(id, dropped)
    }

    override fun getDroppedManga(): Flow<List<Manga>> {
        return mangaDao.getDroppedManga().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateMangaThemeOverride(id: Long, override: Boolean?) {
        mangaDao.updateMangaThemeOverride(id, override)
    }

    override suspend fun updateChapterFlags(id: Long, flags: Int) {
        mangaDao.updateChapterFlags(id, flags)
    }

    override suspend fun updateLocalOverrides(
        id: Long,
        title: String?,
        description: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        genres: List<String>?,
        status: MangaStatus?,
    ) {
        mangaDao.updateUserOverrides(
            id = id,
            title = title,
            description = description,
            author = author,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            genre = genres?.joinToString("|||"),
            status = status?.ordinal,
        )
    }

    override suspend fun clearLocalOverrides(id: Long) {
        // DB first: if the update fails, the stored cover file is still referenced and
        // nothing is lost. Files are only deleted once the DB no longer points at them.
        mangaDao.updateUserOverrides(
            id = id,
            title = null,
            description = null,
            author = null,
            artist = null,
            thumbnailUrl = null,
            genre = null,
            status = null,
        )
        withContext(Dispatchers.IO) { deleteCustomCoverFiles(id) }
    }

    override suspend fun setCustomCover(id: Long, contentUri: String) = withContext(Dispatchers.IO) {
        val coversDir = File(context.filesDir, CUSTOM_COVERS_DIR).apply { mkdirs() }
        // Timestamped filename so Coil's cache (keyed by path) never serves a stale image
        // after the cover is replaced.
        val coverFile = File(coversDir, "${id}_${System.currentTimeMillis()}.img")
        val uri = android.net.Uri.parse(contentUri)
        context.contentResolver.openInputStream(uri)?.use { input ->
            coverFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open selected image")

        // Point the DB at the new file first; only then clean up older files so a failed
        // DB write can never leave the row referencing a deleted path.
        mangaDao.updateUserThumbnail(id, "file://${coverFile.absolutePath}")
        deleteCustomCoverFiles(id, except = coverFile)
    }

    override suspend fun removeCustomCover(id: Long) = withContext(Dispatchers.IO) {
        // DB first for the same crash-safety reason as setCustomCover.
        mangaDao.updateUserThumbnail(id, null)
        deleteCustomCoverFiles(id)
    }

    override suspend fun copyCustomCover(fromMangaId: Long, toMangaId: Long) = withContext(Dispatchers.IO) {
        val sourcePath = mangaDao.getMangaById(fromMangaId)?.userThumbnailUrl?.removePrefix("file://")
        val sourceFile = sourcePath?.let { File(it) }
        if (sourceFile == null || !sourceFile.exists()) return@withContext

        val coversDir = File(context.filesDir, CUSTOM_COVERS_DIR).apply { mkdirs() }
        val newFile = File(coversDir, "${toMangaId}_${System.currentTimeMillis()}.img")
        sourceFile.copyTo(newFile, overwrite = true)

        // DB first for the same crash-safety reason as setCustomCover.
        mangaDao.updateUserThumbnail(toMangaId, "file://${newFile.absolutePath}")
        deleteCustomCoverFiles(toMangaId, except = newFile)
    }

    /** Deletes stored custom cover files for [id], optionally keeping [except]. */
    private fun deleteCustomCoverFiles(id: Long, except: File? = null) {
        File(context.filesDir, CUSTOM_COVERS_DIR)
            .listFiles { f -> f.isFile && f.name.startsWith("${id}_") }
            ?.forEach { if (it != except) it.delete() }
    }

    override fun findDuplicates(): Flow<List<List<Manga>>> =
        getLibraryManga().map { library ->
            library
                .groupBy { it.title.lowercase().trim() }
                .values
                .filter { it.size > 1 }
                .toList()
        }

    override suspend fun getCategoryIdsForManga(mangaId: Long): List<Long> =
        mangaCategoryDao.getCategoryIdsForManga(mangaId)

    override suspend fun linkAlternativeSource(mangaId: Long, altMangaId: Long) {
        // Always store as (min, max) so (A,B) and (B,A) map to the same row and the
        // unique index correctly prevents duplicate symmetric entries.
        val (minId, maxId) = if (mangaId < altMangaId) mangaId to altMangaId else altMangaId to mangaId
        altSourceDao.insert(MangaAlternativeSourceEntity(mangaId = minId, altMangaId = maxId))
    }

    override suspend fun unlinkAlternativeSource(mangaId: Long, altMangaId: Long) {
        val (minId, maxId) = if (mangaId < altMangaId) mangaId to altMangaId else altMangaId to mangaId
        altSourceDao.unlink(minId, maxId)
    }

    override suspend fun getAlternativeSourceIds(mangaId: Long): List<Long> =
        altSourceDao.getAlternativeIdsForMangaSync(mangaId)

    private fun MangaEntity.toDomain(unreadCount: Int = 0, lastRead: Long? = null) = Manga(
        id = id,
        sourceId = sourceId,
        url = url,
        // User overrides take precedence over source values (#998)
        title = userTitle ?: title,
        thumbnailUrl = userThumbnailUrl ?: thumbnailUrl,
        author = userAuthor ?: author,
        artist = userArtist ?: artist,
        description = userDescription ?: description,
        genre = (userGenre ?: genre)?.split("|||")?.filter { it.isNotBlank() } ?: emptyList(),
        status = userStatus?.let { MangaStatus.fromOrdinal(it) } ?: MangaStatus.fromOrdinal(status),
        favorite = favorite,
        initialized = initialized,
        unreadCount = unreadCount,
        autoDownload = autoDownload,
        notes = notes,
        notifyNewChapters = notifyNewChapters,
        dateAdded = dateAdded,
        lastUpdate = lastUpdate,
        lastRead = lastRead,
        // Per-manga reader settings (#260)
        readerDirection = readerDirection,
        readerMode = readerMode,
        readerColorFilter = readerColorFilter,
        readerCustomTintColor = readerCustomTintColor,
        readerBackgroundColor = readerBackgroundColor,
        // Page preloading settings (#264)
        preloadPagesBefore = preloadPagesBefore,
        preloadPagesAfter = preloadPagesAfter,
        contentRating = ContentRating.fromOrdinal(contentRating),
        userCompleted = userCompleted,
        userDropped = userDropped,
        mangaThemeOverride = mangaThemeOverride,
        isUserEdited = userTitle != null || userDescription != null || userAuthor != null ||
            userArtist != null || userThumbnailUrl != null || userGenre != null || userStatus != null,
        // Only app-stored picker images count as a "custom cover". A manual cover URL set
        // via Edit Info also lives in userThumbnailUrl but must not enable the
        // "Remove custom cover" path, which would silently discard that URL override.
        hasCustomCover = userThumbnailUrl?.contains("/$CUSTOM_COVERS_DIR/") == true,
        chapterFlags = chapterFlags,
    )

    private fun Manga.toEntity() = MangaEntity(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        author = author,
        artist = artist,
        description = description,
        genre = genre.joinToString("|||"),
        status = status.ordinal,
        favorite = favorite,
        initialized = initialized,
        autoDownload = autoDownload,
        notes = notes,
        notifyNewChapters = notifyNewChapters,
        dateAdded = dateAdded,
        // Per-manga reader settings (#260)
        readerDirection = readerDirection,
        readerMode = readerMode,
        readerColorFilter = readerColorFilter,
        readerCustomTintColor = readerCustomTintColor,
        readerBackgroundColor = readerBackgroundColor,
        // Page preloading settings (#264)
        preloadPagesBefore = preloadPagesBefore,
        preloadPagesAfter = preloadPagesAfter,
        contentRating = contentRating.ordinal,
        userCompleted = userCompleted,
        userDropped = userDropped,
        mangaThemeOverride = mangaThemeOverride,
        chapterFlags = chapterFlags,
    )

    private companion object {
        /** App-private directory under filesDir holding user-chosen cover images. */
        const val CUSTOM_COVERS_DIR = "custom_covers"
    }
}
