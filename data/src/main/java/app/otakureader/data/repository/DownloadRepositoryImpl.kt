package app.otakureader.data.repository

import android.content.Context
import app.otakureader.core.common.di.ApplicationScope
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.data.download.CbzCreator
import app.otakureader.data.download.ChapterDownloadRequest
import app.otakureader.data.download.DownloadManager
import app.otakureader.data.download.DownloadProvider
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.OrphanScanResult
import app.otakureader.domain.model.ReindexResult
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveDownloadFolderName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val sourceRepository: SourceRepository,
    @ApplicationScope private val scope: CoroutineScope
) : DownloadRepository {

    private val notifier = DownloadNotifier(context)

    init {
        // Keep the system notification in sync with the download queue.
        downloadManager.downloads
            .onEach { notifier.update(it) }
            .launchIn(scope)
    }

    override fun observeDownloads(): Flow<List<DownloadItem>> = downloadManager.downloads

    override suspend fun enqueueChapter(
        mangaId: Long,
        chapterId: Long,
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String,
        pageUrls: List<String>,
        priority: Int
    ) {
        downloadManager.enqueue(
            ChapterDownloadRequest(
                mangaId = mangaId,
                chapterId = chapterId,
                sourceName = sourceName,
                mangaTitle = mangaTitle,
                chapterTitle = chapterTitle,
                pageUrls = pageUrls,
                priority = priority
            )
        )
    }

    override suspend fun pauseDownload(id: Long) {
        downloadManager.pause(id)
    }

    override suspend fun resumeDownload(id: Long) {
        downloadManager.resume(id)
    }

    override suspend fun retryDownload(id: Long) {
        downloadManager.retry(id)
    }

    override suspend fun cancelDownload(id: Long) {
        downloadManager.cancel(id)
    }

    override suspend fun prioritizeDownload(chapterId: Long) {
        downloadManager.prioritize(chapterId)
    }

    override suspend fun prioritizeDownloads(chapterIds: List<Long>) {
        downloadManager.prioritizeAll(chapterIds)
    }

    override suspend fun reorderDownload(chapterId: Long, newPriority: Int) {
        downloadManager.reorder(chapterId, newPriority)
    }

    override suspend fun deleteChapterDownload(
        chapterId: Long,
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String
    ) {
        // Cancel any active job for this chapter before touching the filesystem.
        downloadManager.cancel(chapterId)

        withContext(Dispatchers.IO) {
            DownloadProvider.deleteChapter(context, sourceName, mangaTitle, chapterTitle)
        }
    }

    override suspend fun clearAll() {
        downloadManager.clearAll()
        notifier.cancel()
    }

    override suspend fun isChapterDownloaded(
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String
    ): Boolean = withContext(Dispatchers.IO) {
        DownloadProvider.isChapterDownloaded(context, sourceName, mangaTitle, chapterTitle)
    }

    override suspend fun hasMangaDownloads(
        sourceName: String,
        mangaTitle: String
    ): Boolean = withContext(Dispatchers.IO) {
        DownloadProvider.hasMangaDownloads(context, sourceName, mangaTitle)
    }

    override suspend fun exportChapterAsCbz(
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val chapterDir = DownloadProvider.getChapterDir(context, sourceName, mangaTitle, chapterTitle)
        if (!chapterDir.isDirectory) {
            return@withContext Result.failure(IllegalStateException("Chapter not downloaded"))
        }

        // If the CBZ already exists (e.g. saved during auto-download), nothing to do.
        val existingCbz = DownloadProvider.getCbzFile(context, sourceName, mangaTitle, chapterTitle)
        if (existingCbz.exists()) {
            return@withContext Result.success(Unit)
        }

        // Require at least one loose page image before attempting to pack.
        val hasLoosePages = chapterDir.listFiles()
            ?.any { it.isFile && it.extension.lowercase() in DownloadProvider.PAGE_EXTENSIONS }
            ?: false
        if (!hasLoosePages) {
            return@withContext Result.failure(
                IllegalStateException("No downloaded pages found to export as CBZ")
            )
        }

        val metadata = CbzCreator.ComicInfoMetadata(
            title = chapterTitle,
            series = mangaTitle,
        )
        CbzCreator.createCbz(chapterDir, metadata).map { }
    }

    override suspend fun reindexDownloads(): ReindexResult = withContext(Dispatchers.IO) {
        val rootDir = DownloadProvider.getRootDir(context)
        if (!rootDir.isDirectory) return@withContext ReindexResult(0, 0)

        var verified = 0
        var empty = 0
        val sourceDirs = rootDir.listFiles { f -> f.isDirectory } ?: return@withContext ReindexResult(0, 0)
        for (sourceDir in sourceDirs) {
            val mangaDirs = sourceDir.listFiles { f -> f.isDirectory } ?: continue
            for (mangaDir in mangaDirs) {
                val chapterDirs = mangaDir.listFiles { f -> f.isDirectory } ?: continue
                for (chapterDir in chapterDirs) {
                    val fileList = chapterDir.list() ?: continue
                    val hasContent = fileList.any { name ->
                        name == CbzCreator.CBZ_FILE_NAME ||
                            name.substringAfterLast('.', "").lowercase() in DownloadProvider.PAGE_EXTENSIONS
                    }
                    if (hasContent) verified++ else empty++
                }
            }
        }
        ReindexResult(verified, empty)
    }

    override suspend fun scanOrphanedDownloads(): OrphanScanResult = withContext(Dispatchers.IO) {
        val orphans = findOrphanedChapterDirs()
        val totalBytes = orphans.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        OrphanScanResult(count = orphans.size, sizeBytes = totalBytes)
    }

    override suspend fun deleteOrphanedDownloads(): OrphanScanResult = withContext(Dispatchers.IO) {
        val rootDir = DownloadProvider.getRootDir(context)
        val orphans = findOrphanedChapterDirs()
        var deletedCount = 0
        var deletedBytes = 0L
        for (dir in orphans) {
            val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (dir.deleteRecursively()) {
                deletedCount++
                deletedBytes += size
                deleteEmptyParents(dir, rootDir)
            }
        }
        OrphanScanResult(count = deletedCount, sizeBytes = deletedBytes)
    }

    /**
     * Removes now-empty manga/source directories left behind after a chapter dir was deleted,
     * stopping at (and never deleting) the downloads root itself.
     */
    private fun deleteEmptyParents(deletedDir: File, rootDir: File) {
        var parent = deletedDir.parentFile
        // Stop at the downloads root, a non-empty dir, or a failed delete.
        while (parent != null &&
            parent != rootDir &&
            parent.listFiles()?.isEmpty() == true &&
            parent.delete()
        ) {
            parent = parent.parentFile
        }
    }

    /**
     * Returns chapter directories on disk that have no matching record in the database.
     * A directory is "orphaned" when its parent manga was deleted from the library without
     * cleaning up the downloaded files.
     *
     * The directory path is derived from the same folder-name resolution used when the
     * download was originally created: [resolveDownloadFolderName] / sanitize(title) /
     * sanitize(name). This must stay in lockstep with every enqueue/delete call site — a
     * mismatch here would make every real download look "orphaned" and get deleted by
     * [deleteOrphanedDownloads].
     */
    private suspend fun findOrphanedChapterDirs(): List<File> {
        val rootDir = DownloadProvider.getRootDir(context)
        if (!rootDir.isDirectory) return emptyList()

        // Build the set of expected chapter dir absolute paths from the database.
        // Exactly 2 queries: all manga + all chapters, joined in memory — avoids an
        // N+1 query per manga which would stall on large libraries.
        val rootParent = rootDir.parentFile ?: return emptyList()
        val mangaById = mangaDao.getAllMangaOnce().associateBy { it.id }
        val sourceNameById = mangaById.values
            .map { it.sourceId }
            .distinct()
            .associateWith { sourceRepository.resolveDownloadFolderName(it) }
        val expectedPaths = buildSet<String> {
            for (chapter in chapterDao.getAllChaptersOnce()) {
                val manga = mangaById[chapter.mangaId] ?: continue
                val dir = DownloadProvider.getChapterDir(
                    rootParent,
                    sourceNameById.getValue(manga.sourceId),
                    manga.title,
                    chapter.name,
                )
                add(dir.absolutePath)
            }
        }

        // Walk 3 levels: source / manga / chapter
        val orphans = mutableListOf<File>()
        val sourceDirs = rootDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        for (sourceDir in sourceDirs) {
            val mangaDirs = sourceDir.listFiles { f -> f.isDirectory } ?: continue
            for (mangaDir in mangaDirs) {
                val chapterDirs = mangaDir.listFiles { f -> f.isDirectory } ?: continue
                for (chapterDir in chapterDirs) {
                    if (chapterDir.absolutePath !in expectedPaths) {
                        orphans += chapterDir
                    }
                }
            }
        }
        return orphans
    }

    override suspend fun migrateChapterDownload(
        fromSourceName: String,
        fromMangaTitle: String,
        fromChapterName: String,
        toSourceName: String,
        toMangaTitle: String,
        toChapterName: String,
        copy: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        DownloadProvider.migrateChapterDownload(
            context,
            fromSourceName,
            fromMangaTitle,
            fromChapterName,
            toSourceName,
            toMangaTitle,
            toChapterName,
            copy
        )
    }

    override suspend fun migrateSourceFolderNames(): Int = withContext(Dispatchers.IO) {
        val rootDir = DownloadProvider.getRootDir(context)
        val candidateIds = rootDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { it.name.toLongOrNull() }
            ?: return@withContext 0
        val resolvedNames = candidateIds.associate { id ->
            id.toString() to sourceRepository.resolveDownloadFolderName(id)
        }
        DownloadProvider.migrateSourceFolderNames(rootDir, resolvedNames)
    }
}
