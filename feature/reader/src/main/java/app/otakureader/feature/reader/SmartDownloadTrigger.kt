package app.otakureader.feature.reader

import android.content.Context
import android.os.StatFs
import app.otakureader.core.common.network.NetworkMonitor
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.model.SmartDownloadRule
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveDownloadFolderName
import app.otakureader.core.common.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors reading progress and triggers smart (auto) downloads when configured.
 *
 * Rules are evaluated every time the reader reports progress:
 * - If progress ≥ threshold and manga is favorited (or favoritesOnly = false)
 * - And Wi-Fi is available (or wifiOnly = false)
 * - And free storage ≥ minimum
 * → Queue next N unread chapters for download.
 */
@Singleton
class SmartDownloadTrigger @Inject constructor(
    private val context: Context,
    private val generalPreferences: GeneralPreferences,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val downloadRepository: DownloadRepository,
    private val sourceRepository: SourceRepository,
    private val networkMonitor: NetworkMonitor,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /**
     * Call this whenever reader progress updates.
     *
     * @param mangaId The manga being read.
     * @param chapterId The current chapter.
     * @param progress Normalized progress (0.0–1.0).
     */
    fun onProgress(mangaId: Long, chapterId: Long, progress: Float) {
        scope.launch {
            val rule = loadRule()
            if (!rule.enabled) return@launch
            if (progress < rule.progressThreshold) return@launch

            // Fetch manga once — used for both the favorites check and download titles
            val manga = mangaRepository.getMangaById(mangaId) ?: return@launch

            // Check favorites-only constraint
            if (rule.favoritesOnly && !manga.favorite) return@launch

            // Check Wi-Fi constraint
            if (rule.wifiOnly && !isUnmeteredNetwork()) return@launch

            // Check storage constraint
            if (getFreeStorageMb() < rule.minFreeStorageMb) return@launch

            // Queue next N unread chapters
            val chapters = chapterRepository.getChaptersByMangaIdSync(mangaId)
            val currentIndex = chapters.indexOfFirst { it.id == chapterId }
            if (currentIndex < 0) return@launch

            val toDownload = chapters
                .drop(currentIndex + 1)
                .filter { !it.read }
                .take(rule.chaptersAhead)

            val sourceName = sourceRepository.resolveDownloadFolderName(manga.sourceId)
            for (chapter in toDownload) {
                downloadRepository.enqueueChapter(
                    mangaId = mangaId,
                    chapterId = chapter.id,
                    sourceName = sourceName,
                    mangaTitle = manga.title,
                    chapterTitle = chapter.name,
                )
            }
        }
    }

    private suspend fun loadRule(): SmartDownloadRule {
        return SmartDownloadRule(
            enabled = generalPreferences.smartDownloadEnabled.first(),
            chaptersAhead = generalPreferences.smartDownloadChaptersAhead.first(),
            progressThreshold = generalPreferences.smartDownloadThreshold.first(),
            wifiOnly = generalPreferences.smartDownloadWifiOnly.first(),
            favoritesOnly = generalPreferences.smartDownloadFavoritesOnly.first(),
            minFreeStorageMb = generalPreferences.smartDownloadMinStorageMb.first(),
        )
    }

    private fun isUnmeteredNetwork(): Boolean = networkMonitor.isUnmetered()

    private fun getFreeStorageMb(): Long {
        val stat = StatFs(context.getExternalFilesDir(null)?.path ?: return 0L)
        val availableBlocks = stat.availableBlocksLong
        val blockSize = stat.blockSizeLong
        return (availableBlocks * blockSize) / (1024 * 1024)
    }
}
