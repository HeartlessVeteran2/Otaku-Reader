package app.otakureader.data.repository

import android.content.Context
import app.otakureader.core.common.di.ApplicationScope
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.data.download.CbzCreator
import app.otakureader.data.download.ChapterDownloadRequest
import app.otakureader.data.download.DownloadManager
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.data.download.DownloadProvider
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.OrphanScanResult
import app.otakureader.domain.model.ReindexResult
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.SourceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val sourceRepository: SourceRepository,
    private val downloadPreferences: DownloadPreferences,
    @param:ApplicationScope private val scope: CoroutineScope
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

    override suspend fun getMangaIdsWithDownloads(
        mangaKeys: Map<Long, Pair<String, String>>
    ): Set<Long> = withContext(Dispatchers.IO) {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val downloadedDirKeys = DownloadProvider.getMangaDirsWithDownloads(root)
        mangaKeys.filterValues { (sourceName, mangaTitle) ->
            (DownloadProvider.sanitize(sourceName) to DownloadProvider.sanitize(mangaTitle)) in downloadedDirKeys
        }.keys
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
     * download was originally created: [downloadFolderNameFor] / sanitize(title) /
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
            .associateWith { downloadFolderNameFor(it) }
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
        // The *display* name, deliberately not downloadFolderNameFor(): that answers "where do
        // this source's chapters live right now", which for an unmigrated source is the number
        // this loop is trying to rename away from. Asking it here would build an identity map and
        // rename nothing — which is exactly what this worker did before #1256, when the resolver
        // was `sourceId.toString()`.
        val resolvedNames = candidateIds.mapNotNull { id ->
            displayNameFor(id)?.let { id.toString() to it }
        }.toMap()
        // Only the renames that actually happened, which is why the provider returns them rather
        // than a count. Recording a target merely because it exists on disk would be exactly wrong
        // in the skip case that matters: the provider refuses to rename onto an existing directory,
        // so that directory belongs to some *other* source, and recording it here would make every
        // read for this source resolve to the wrong library — defeating the numeric-first ordering
        // below, since a recorded folder outranks it.
        val renamed = DownloadProvider.migrateSourceFolderNames(rootDir, resolvedNames)
        // Written one at a time, immediately, rather than batched after the loop: a process death
        // mid-migration then leaves at most one moved folder unrecorded instead of all of them.
        // That residual window is not fully closed — a crash between a rename and its write, plus a
        // later uninstall of that same extension, leaves those downloads unreachable from the app
        // (the files are still on disk under a readable name). Closing it completely needs a
        // journal, which is more machinery than a one-time migration warrants.
        renamed.forEach { (numeric, newName) ->
            numeric.toLongOrNull()?.let { downloadPreferences.setSourceFolderName(it, newName) }
        }
        // Bump first, clear second. A resolve already in flight captured the old generation, so
        // its write is dropped rather than repopulating the cache with a pre-rename answer.
        folderGeneration.incrementAndGet()
        folderNames.clear()
        renamed.size
    }

    /**
     * Cache for [downloadFolderNameFor].
     *
     * Not premature: `LibraryViewModel` resolves one folder name per library entry to build its
     * download badges, so an uncached resolve would mean a source-list scan and two `isDirectory`
     * calls per manga, every time the library is observed. The previous implementation was
     * `sourceId.toString()`, so there was no cost to inherit.
     *
     * Cleared by [migrateSourceFolderNames], which is the one thing that moves a folder out from
     * under a cached answer. [folderGeneration] guards the window around that: a resolve that
     * started before the rename must not write its pre-rename answer back into the cleared cache.
     */
    private val folderNames = ConcurrentHashMap<Long, String>()

    /** Incremented whenever a rename invalidates [folderNames]; see [downloadFolderNameFor]. */
    private val folderGeneration = AtomicInteger(0)

    override suspend fun downloadFolderNameFor(sourceId: Long): String {
        folderNames[sourceId]?.let { return it }
        val generation = folderGeneration.get()
        val resolved = resolveFolderName(sourceId)
        // Only cache an answer computed against the layout that is still current. Without this a
        // lookup that raced the migration could reinstate the numeric name after the folder moved.
        if (folderGeneration.get() == generation) folderNames[sourceId] = resolved
        return resolved
    }

    /**
     * Decides where a source's chapters actually live. See [DownloadRepository.downloadFolderNameFor]
     * for why disk, not the display name, has the final say.
     *
     * The order of the branches is the whole design, and each one is above the next because
     * answering with the lower one in that state would hide files:
     */
    private suspend fun resolveFolderName(sourceId: Long): String {
        val numeric = sourceId.toString()
        val recorded = downloadPreferences.sourceFolderNames.first()[sourceId]
        // Nulled out when some *other* source is recorded under this name, so the ownership check
        // covers both the "folder already exists" branch and the "nothing on disk yet" one. Doing
        // it per-branch is what let the newcomer case slip through: guarding branch 3 alone just
        // fell through to branch 4, which returned the same name for the same wrong reason.
        val display = displayNameFor(sourceId)?.takeIf { !ownedByAnother(it, sourceId) }
        return withContext(Dispatchers.IO) {
            val root = DownloadProvider.getRootDir(context)
            when {
                // 1. A folder this app renamed, and it is still there. Authoritative even when the
                //    source is long uninstalled, which is the case no amount of re-derivation can
                //    recover: there is no display name left to compute.
                recorded != null && File(root, recorded).isDirectory -> recorded
                // 2. Chapters still filed under the number — unmigrated, or the migration could
                //    not claim its target name. Checked *before* the display name on purpose: when
                //    both directories exist the display one is not necessarily ours (the migration
                //    skips a target that already exists rather than merging into it), while the
                //    numeric one can only ever be this source's.
                File(root, numeric).isDirectory -> numeric
                // 3. A display-name folder with nothing under the number: normal, post-migration.
                //    `display` is already null when another source owns that name, because a
                //    display name is not unique over time: a source that migrated to `MangaDex/`
                //    and was then uninstalled keeps its recording, and a *different* source later
                //    installed under the same name has neither a recording nor a numeric folder —
                //    so it would otherwise adopt the old source's downloads and write its own in
                //    among them. displayNameFor's own collision check cannot catch that; it only
                //    compares sources that are currently loaded, and the previous owner is gone.
                display != null && File(root, display).isDirectory -> display
                // 4. Nothing on disk yet, so nothing to lose; new downloads get the readable name
                //    when one is available and the number otherwise.
                else -> display ?: numeric
            }
        }
    }

    /**
     * The sanitized display name for a source key, or null when no readable name may be used.
     *
     * Null covers two cases that must both keep the numeric key. The source may not be loaded at
     * all — an uninstalled extension — and its downloads have to stay readable under the number.
     * Or two loaded sources may share a display name, which both backends shipping a source of
     * the same name makes realistic: filing both under one folder would interleave two catalogues
     * in one tree. That collision is detectable from the loaded list without touching disk, so
     * the migration and every read reach the same verdict by construction rather than by luck.
     */
    /** Whether [folderName] is recorded as belonging to some source other than [sourceId]. */
    private suspend fun ownedByAnother(folderName: String, sourceId: Long): Boolean =
        downloadPreferences.sourceFolderNames.first()
            .any { (key, name) -> key != sourceId && name == folderName }

    private suspend fun displayNameFor(sourceId: Long): String? {
        val name = sourceRepository.getSourceByKey(sourceId)?.name ?: return null
        val sanitized = DownloadProvider.sanitize(name)
        if (sanitized.isBlank() || sanitized == sourceId.toString()) return null
        val sharingName = sourceRepository.getSources().first()
            .count { DownloadProvider.sanitize(it.name) == sanitized }
        return if (sharingName > 1) null else sanitized
    }
}
