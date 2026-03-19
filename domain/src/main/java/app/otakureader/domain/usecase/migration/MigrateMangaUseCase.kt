package app.otakureader.domain.usecase.migration

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationResult
import app.otakureader.domain.model.MigrationStatus
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Use case for migrating a manga from one source to another.
 * Handles both MOVE (replace) and COPY (keep both) modes.
 * Preserves reading history, bookmarks, categories, tracker links, and downloaded chapters.
 */
class MigrateMangaUseCase @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val categoryRepository: CategoryRepository,
    private val sourceRepository: SourceRepository,
    private val downloadRepository: DownloadRepository,
    private val trackRepository: TrackRepository
) {
    /**
     * Migrate a manga to a new source.
     * @param sourceManga The manga to migrate
     * @param targetCandidate The target manga candidate from the new source
     * @param mode Migration mode (MOVE or COPY)
     * @return Result with MigrationResult containing status and details
     */
    suspend operator fun invoke(
        sourceManga: Manga,
        targetCandidate: MigrationCandidate,
        mode: MigrationMode
    ): Result<MigrationResult> {
        return try {
            // Check if target manga already exists in library
            val existingTarget = mangaRepository.getMangaBySourceAndUrl(
                sourceId = targetCandidate.sourceId,
                url = targetCandidate.url
            )

            val targetMangaId = if (existingTarget != null) {
                // Target already exists, use it
                existingTarget.id
            } else {
                // Create new manga entry for target
                val newManga = targetCandidate.toManga(
                    favorite = sourceManga.favorite,
                    autoDownload = sourceManga.autoDownload
                )
                mangaRepository.insertManga(newManga)
            }

            // Fetch detailed manga info and chapters from new source
            val sourceMangaForFetch = SourceManga(
                url = targetCandidate.url,
                title = targetCandidate.title,
                thumbnailUrl = targetCandidate.thumbnailUrl
            )

            val detailsResult = sourceRepository.getMangaDetails(
                sourceId = targetCandidate.sourceId.toString(),
                manga = sourceMangaForFetch
            )

            val chaptersResult = sourceRepository.getChapterList(
                sourceId = targetCandidate.sourceId.toString(),
                manga = sourceMangaForFetch
            )

            if (detailsResult.isFailure || chaptersResult.isFailure) {
                return Result.failure(
                    Exception("Failed to fetch manga details or chapters from target source")
                )
            }

            val targetChapters = chaptersResult.getOrNull() ?: emptyList()

            // Get source manga chapters
            val sourceChapters = chapterRepository.getChaptersByMangaIdSync(sourceManga.id)

            // Match chapters and migrate reading progress and downloads
            val matchedCount = matchAndMigrateChapters(
                sourceManga = sourceManga,
                sourceChapters = sourceChapters,
                targetManga = targetCandidate,
                targetMangaId = targetMangaId,
                targetChapters = targetChapters,
                mode = mode
            )

            // Migrate categories
            if (sourceManga.categoryIds.isNotEmpty()) {
                sourceManga.categoryIds.forEach { categoryId ->
                    try {
                        categoryRepository.addMangaToCategory(targetMangaId, categoryId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Propagate cancellation immediately
                        throw e
                    } catch (e: Exception) {
                        // Category might already be assigned, ignore
                    }
                }
            }

            // Migrate tracker links – per-entry error handling so a single
            // tracker failure does not abort an otherwise-successful migration.
            val trackerEntries = trackRepository.observeEntriesForManga(sourceManga.id).first()
            var failedTrackers = 0
            trackerEntries.forEach { entry ->
                try {
                    val migratedEntry = entry.copy(mangaId = targetMangaId)
                    trackRepository.upsertEntry(migratedEntry)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Propagate cancellation immediately
                    throw e
                } catch (e: Exception) {
                    // Individual tracker migration failure is non-fatal; continue
                    // with the remaining entries so partial progress is preserved.
                    failedTrackers++
                    System.err.println("MigrateMangaUseCase: Failed to migrate tracker entry for tracker ${entry.trackerId}: ${e.message}")
                }
            }
            if (failedTrackers > 0) {
                System.err.println("MigrateMangaUseCase: Migration completed with $failedTrackers tracker failure(s) out of ${trackerEntries.size} total")
            }

            // Handle MOVE vs COPY mode.
            // Destructive operations are performed last so that additive steps
            // (chapters, categories, trackers) complete first. This ordering
            // reduces the window for inconsistent state when a failure occurs.
            // Note: These operations are not atomic. If a failure occurs between
            // operations, manual cleanup may be required. Consider using database
            // transactions if stronger consistency guarantees are needed.
            when (mode) {
                MigrationMode.MOVE -> {
                    // Remove category associations from old manga
                    if (sourceManga.categoryIds.isNotEmpty()) {
                        sourceManga.categoryIds.forEach { categoryId ->
                            try {
                                categoryRepository.removeMangaFromCategory(sourceManga.id, categoryId)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                // Propagate cancellation immediately
                                throw e
                            } catch (e: Exception) {
                                // Category removal is non-critical; continue
                            }
                        }
                    }
                    // Tracker entries are migrated via upsertEntry() which replaces
                    // by (trackerId, remoteId), so the old entries are effectively
                    // moved to the target manga. No explicit deletion needed.
                    mangaRepository.deleteManga(sourceManga.id)
                    // Chapters will be cascade deleted by foreign key
                }
                MigrationMode.COPY -> {
                    // Keep both, do nothing
                }
            }

            Result.success(
                MigrationResult(
                    originalMangaId = sourceManga.id,
                    newMangaId = targetMangaId,
                    chaptersMatched = matchedCount,
                    status = MigrationStatus.COMPLETED
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Match chapters between source and target, migrate reading progress and downloads.
     * Returns the number of chapters successfully matched.
     */
    private suspend fun matchAndMigrateChapters(
        sourceManga: Manga,
        sourceChapters: List<Chapter>,
        targetManga: MigrationCandidate,
        targetMangaId: Long,
        targetChapters: List<SourceChapter>,
        mode: MigrationMode
    ): Int {
        var matchedCount = 0

        // Create a map of chapter numbers to source chapters for quick lookup
        val sourceChapterMap = sourceChapters
            .filter { it.chapterNumber >= 0 }
            .associateBy { it.chapterNumber }

        // Source names for download migration
        val fromSourceName = sourceManga.sourceId.toString()
        val fromMangaTitle = sourceManga.title
        val toSourceName = targetManga.sourceId.toString()
        val toMangaTitle = targetManga.title

        // Insert target chapters with matched reading progress
        val chaptersToInsert = targetChapters.mapIndexed { index, targetChapter ->
            val sourceChapter = sourceChapterMap[targetChapter.chapterNumber]

            if (sourceChapter != null) {
                matchedCount++

                // Migrate downloaded chapters if they exist
                val isDownloaded = downloadRepository.isChapterDownloaded(
                    sourceName = fromSourceName,
                    mangaTitle = fromMangaTitle,
                    chapterTitle = sourceChapter.name
                )

                if (isDownloaded) {
                    // Migrate downloads: copy for COPY mode, move for MOVE mode
                    downloadRepository.migrateChapterDownload(
                        fromSourceName = fromSourceName,
                        fromMangaTitle = fromMangaTitle,
                        fromChapterName = sourceChapter.name,
                        toSourceName = toSourceName,
                        toMangaTitle = toMangaTitle,
                        toChapterName = targetChapter.name,
                        copy = mode == MigrationMode.COPY
                    )
                }
            }

            Chapter(
                id = 0L, // Auto-generate
                mangaId = targetMangaId,
                url = targetChapter.url,
                name = targetChapter.name,
                scanlator = targetChapter.scanlator,
                read = sourceChapter?.read ?: false,
                bookmark = sourceChapter?.bookmark ?: false,
                lastPageRead = sourceChapter?.lastPageRead ?: 0,
                chapterNumber = targetChapter.chapterNumber,
                dateUpload = targetChapter.dateUpload
            )
        }

        chapterRepository.insertChapters(chaptersToInsert)

        return matchedCount
    }

    private fun MigrationCandidate.toManga(
        favorite: Boolean = false,
        autoDownload: Boolean = false
    ) = Manga(
        id = 0L, // Auto-generate
        sourceId = sourceId,
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        author = author,
        artist = artist,
        description = description,
        genre = genre,
        status = status,
        favorite = favorite,
        initialized = true,
        autoDownload = autoDownload
    )
}
