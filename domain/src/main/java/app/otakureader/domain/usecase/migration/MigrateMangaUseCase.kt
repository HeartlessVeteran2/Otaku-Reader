package app.otakureader.domain.usecase.migration

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationResult
import app.otakureader.domain.model.MigrationStatus
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import javax.inject.Inject

/**
 * Use case for migrating a manga from one source to another.
 * Handles both MOVE (replace) and COPY (keep both) modes.
 * Preserves reading history, bookmarks, and categories.
 */
class MigrateMangaUseCase @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val categoryRepository: CategoryRepository,
    private val sourceRepository: SourceRepository
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

            // Match chapters and migrate reading progress
            val matchedCount = matchAndMigrateChapters(
                sourceChapters = sourceChapters,
                targetChapters = targetChapters,
                targetMangaId = targetMangaId
            )

            // Migrate categories
            if (sourceManga.categoryIds.isNotEmpty()) {
                sourceManga.categoryIds.forEach { categoryId ->
                    try {
                        categoryRepository.addMangaToCategory(targetMangaId, categoryId)
                    } catch (e: Exception) {
                        // Category might already be assigned, ignore
                    }
                }
            }

            // Handle MOVE vs COPY mode
            when (mode) {
                MigrationMode.MOVE -> {
                    // Remove old manga and its chapters
                    if (sourceManga.categoryIds.isNotEmpty()) {
                        sourceManga.categoryIds.forEach { categoryId ->
                            try {
                                categoryRepository.removeMangaFromCategory(sourceManga.id, categoryId)
                            } catch (e: Exception) {
                                // Ignore errors
                            }
                        }
                    }
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
     * Match chapters between source and target, and migrate reading progress.
     * Returns the number of chapters successfully matched.
     */
    private suspend fun matchAndMigrateChapters(
        sourceChapters: List<Chapter>,
        targetChapters: List<SourceChapter>,
        targetMangaId: Long
    ): Int {
        var matchedCount = 0

        // Create a map of chapter numbers to source chapters for quick lookup
        val sourceChapterMap = sourceChapters
            .filter { it.chapterNumber >= 0 }
            .associateBy { it.chapterNumber }

        // Insert target chapters with matched reading progress
        val chaptersToInsert = targetChapters.mapIndexed { index, targetChapter ->
            val sourceChapter = sourceChapterMap[targetChapter.chapterNumber]

            if (sourceChapter != null) {
                matchedCount++
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
