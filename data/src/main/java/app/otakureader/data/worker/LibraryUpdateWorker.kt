package app.otakureader.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.UpdateLibraryMangaUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background worker that checks for new chapters in the library.
 * This worker fetches the latest chapters for all favorite manga and updates the database.
 */
@HiltWorker
class LibraryUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val getLibraryManga: GetLibraryMangaUseCase,
    private val updateLibraryManga: UpdateLibraryMangaUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Get all library manga
            val libraryManga = getLibraryManga().first()

            if (libraryManga.isEmpty()) {
                return Result.success()
            }

            var totalNewChapters = 0
            var failedUpdates = 0

            // Update each manga
            for (manga in libraryManga) {
                val result = updateLibraryManga(manga)

                result.onSuccess { newChapters ->
                    totalNewChapters += newChapters
                }.onFailure {
                    failedUpdates++
                }
            }

            // Consider it a success if at least some manga were updated successfully
            if (failedUpdates == libraryManga.size) {
                Result.failure()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "library_update"
    }
}
