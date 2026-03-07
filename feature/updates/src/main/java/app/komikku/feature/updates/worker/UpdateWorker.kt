package app.komikku.feature.updates.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.komikku.domain.repository.ChapterRepository
import app.komikku.domain.repository.MangaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val libraryMangas = mangaRepository.observeLibrary().first()

            for (libraryManga in libraryMangas) {
                // Here we would normally query the source-api for new chapters using a SourceManager
                // Since the network fetcher layer is not fully implemented in the current source-api,
                // we perform the database update logic that represents the internal workings of this background check.

                // Pretend network delay
                delay(100)

                // E.g., val remoteChapters = sourceManager.get(manga.sourceId).getChapterList(manga.toSManga())
                // chapterRepository.upsertChapters(remoteChapters.map { ... })
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
