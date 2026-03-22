package app.otakureader.domain.usecase

import app.otakureader.domain.repository.MangaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Use case to remove multiple manga from the library at once.
 */
class BulkRemoveFromLibraryUseCase @Inject constructor(
    private val mangaRepository: MangaRepository
) {
    /**
     * Remove multiple manga from the library.
     *
     * **M-8 — Input validation:** Empty list and non-positive IDs are rejected early.
     *
     * **M-9 — Parallel processing:** IDs are processed concurrently.
     *
     * @param mangaIds List of manga IDs to remove (must be non-empty, all IDs > 0)
     * @param deleteDownloads Whether to also delete downloaded chapters
     * @return Result with count of successfully removed manga
     */
    suspend operator fun invoke(
        mangaIds: List<Long>,
        deleteDownloads: Boolean = false
    ): BulkRemoveResult {
        // M-8: Validate input before processing.
        if (mangaIds.isEmpty()) {
            return BulkRemoveResult(successCount = 0, failCount = 0, totalCount = 0,
                errors = listOf("No manga IDs provided."))
        }
        val invalidIds = mangaIds.filter { it <= 0 }
        if (invalidIds.isNotEmpty()) {
            return BulkRemoveResult(
                successCount = 0,
                failCount = invalidIds.size,
                totalCount = mangaIds.size,
                errors = invalidIds.map { "Invalid manga ID: $it (must be > 0)" }
            )
        }

        // M-9: Process IDs in parallel using coroutineScope + async.
        val mutex = Mutex()
        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()

        coroutineScope {
            mangaIds.map { mangaId ->
                async {
                    try {
                        mangaRepository.removeFromFavorites(mangaId)
                        if (deleteDownloads) {
                            mangaRepository.deleteDownloadsForManga(mangaId)
                        }
                        mutex.withLock { successCount++ }
                    } catch (e: Exception) {
                        mutex.withLock {
                            failCount++
                            errors.add("Error removing manga $mangaId: ${e.message}")
                        }
                    }
                }
            }.awaitAll()
        }

        return BulkRemoveResult(
            successCount = successCount,
            failCount = failCount,
            totalCount = mangaIds.size,
            errors = errors
        )
    }

    data class BulkRemoveResult(
        val successCount: Int,
        val failCount: Int,
        val totalCount: Int,
        val errors: List<String>
    )
}
