package app.otakureader.domain.usecase

import app.otakureader.domain.repository.MangaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Use case to add multiple manga to the library at once.
 * Used for bulk operations in browse/search screens.
 */
class BulkAddToLibraryUseCase @Inject constructor(
    private val mangaRepository: MangaRepository
) {
    /**
     * Add multiple manga to the library.
     *
     * **M-8 — Input validation:** An empty [mangaIds] list or a list containing
     * non-positive IDs is rejected early to prevent unexpected database behaviour.
     *
     * **M-9 — Parallel processing:** IDs are now processed concurrently using
     * [coroutineScope] + [async] instead of sequentially with [forEach], giving
     * much better throughput for large lists.
     *
     * @param mangaIds List of manga IDs to add (must be non-empty, all IDs > 0)
     * @param categoryId Optional category to assign (null = default category)
     * @return Result with count of successfully added manga
     */
    suspend operator fun invoke(mangaIds: List<Long>, categoryId: Long? = null): BulkResult {
        // M-8: Validate input before processing.
        if (mangaIds.isEmpty()) {
            return BulkResult(successCount = 0, failCount = 0, totalCount = 0,
                errors = listOf("No manga IDs provided."))
        }
        val invalidIds = mangaIds.filter { it <= 0 }
        if (invalidIds.isNotEmpty()) {
            return BulkResult(
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
                        val manga = mangaRepository.getMangaById(mangaId)
                        if (manga != null) {
                            mangaRepository.addToFavorites(mangaId)
                            categoryId?.let { mangaRepository.addMangaToCategory(mangaId, it) }
                            mutex.withLock { successCount++ }
                        } else {
                            mutex.withLock {
                                failCount++
                                errors.add("Manga not found: $mangaId")
                            }
                        }
                    } catch (e: Exception) {
                        mutex.withLock {
                            failCount++
                            errors.add("Error adding manga $mangaId: ${e.message}")
                        }
                    }
                }
            }.awaitAll()
        }

        return BulkResult(
            successCount = successCount,
            failCount = failCount,
            totalCount = mangaIds.size,
            errors = errors
        )
    }

    data class BulkResult(
        val successCount: Int,
        val failCount: Int,
        val totalCount: Int,
        val errors: List<String>
    ) {
        val isSuccess: Boolean get() = failCount == 0
        val isPartialSuccess: Boolean get() = successCount > 0 && failCount > 0
    }
}
