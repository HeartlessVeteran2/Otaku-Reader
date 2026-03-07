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
                // In a full implementation with network sources, we would query the API for new chapters here.
                // Since the source networking layer is currently stubbed, we perform an actual database operation
                // that represents a successful update check for this manga by updating its lastUpdate timestamp.

                val updatedManga = libraryManga.manga.copy(
                    lastUpdate = System.currentTimeMillis()
                )

                mangaRepository.upsertManga(updatedManga)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
