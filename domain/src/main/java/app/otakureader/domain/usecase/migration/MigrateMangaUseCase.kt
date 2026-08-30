package app.otakureader.domain.usecase.migration

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.model.MigrationFlag
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationResult
import app.otakureader.domain.model.MigrationStatus
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveSourceId
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
     * @param flags Which data categories to carry over. Defaults to all (pre-#1192-PR-6 behavior).
     * @return Result with MigrationResult containing status and details
     */
    @Suppress("ThrowsCount", "LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
    suspend operator fun invoke(
        sourceManga: Manga,
        targetCandidate: MigrationCandidate,
        mode: MigrationMode,
        flags: Set<MigrationFlag> = MigrationFlag.entries.toSet()
    ): Result<MigrationResult> {
        return try {
            // Resolved before anything is written. Doing it after the insert below would leave a
            // library row for a source that cannot be reached — a target entry that can never be
            // initialised and that the user did not ask to keep.
            val targetSourceId = sourceRepository.resolveSourceId(targetCandidate.sourceId)
                ?: return Result.failure(
                    IllegalStateException("Target source not found: ${targetCandidate.sourceId}")
                )

            // Check if target manga already exists in library
            val existingTarget = mangaRepository.getMangaBySourceAndUrl(
                sourceId = targetCandidate.sourceId,
                url = targetCandidate.url
            )

            val targetMangaId = if (existingTarget != null) {
                // Target already exists, use it
                existingTarget.id
            } else {
                // Create new manga entry for target, carrying over notes (Komikku's NOTES
                // migration flag) directly at insert time — there's no existing target row to
                // conflict with, so this avoids a separate update query.
                val newManga = targetCandidate.toManga(
                    favorite = sourceManga.favorite,
                    autoDownload = sourceManga.autoDownload,
                    notes = if (MigrationFlag.NOTES in flags) sourceManga.notes else null
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
                sourceId = targetSourceId,
                manga = sourceMangaForFetch
            )

            val chaptersResult = sourceRepository.getChapterList(
                sourceId = targetSourceId,
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
                mode = mode,
                flags = flags
            )

            // A newly-created target already got its notes set at insert time above. An
            // existing target needs an explicit update — but only when it has no notes of its
            // own yet, so migration never clobbers notes the user already wrote against it.
            // Re-fetches the target's current notes rather than reusing the `existingTarget`
            // snapshot taken before this point: several suspending calls (source detail/chapter
            // fetches, chapter matching, download migration) run in between, and a note the user
            // added during that window would otherwise be silently overwritten.
            if (MigrationFlag.NOTES in flags && existingTarget != null && !sourceManga.notes.isNullOrBlank()) {
                try {
                    val currentNotes = mangaRepository.getMangaById(targetMangaId)?.notes
                    if (currentNotes.isNullOrBlank()) {
                        mangaRepository.updateMangaNote(targetMangaId, sourceManga.notes)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Notes are a non-critical field; a failure here shouldn't abort migration.
                }
            }

            // Migrate custom cover art (Otaku-exclusive user-set cover override).
            if (MigrationFlag.CUSTOM_COVER in flags && sourceManga.hasCustomCover) {
                try {
                    mangaRepository.copyCustomCover(sourceManga.id, targetMangaId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Custom cover is a non-critical field; a failure here shouldn't abort migration.
                }
            }

            // Migrate categories
            if (MigrationFlag.CATEGORIES in flags && sourceManga.categoryIds.isNotEmpty()) {
                sourceManga.categoryIds.forEach { categoryId ->
                    try {
                        categoryRepository.addMangaToCategory(targetMangaId, categoryId)
                    } catch (e: CancellationException) {
                        // Propagate cancellation immediately
                        throw e
                    } catch (e: Exception) {
                        // Category might already be assigned, ignore
                    }
                }
            }

            // Migrate tracker links – per-entry error handling so a single
            // tracker failure does not abort an otherwise-successful migration.
            if (MigrationFlag.TRACKING in flags) {
                val trackerEntries = trackRepository.observeEntriesForManga(sourceManga.id).first()
                var failedTrackers = 0
                trackerEntries.forEach { entry ->
                    try {
                        val migratedEntry = entry.copy(mangaId = targetMangaId)
                        trackRepository.upsertEntry(migratedEntry)
                    } catch (e: CancellationException) {
                        // Propagate cancellation immediately
                        throw e
                    } catch (e: Exception) {
                        // Individual tracker migration failure is non-fatal; continue
                        // with the remaining entries so partial progress is preserved.
                        failedTrackers++
                        // Domain layer uses System.err.println (not android.util.Log) as it's pure Kotlin
                        // System.err.println("Failed to migrate tracker entry for tracker ${entry.trackerId}: ${e.message}")
                    }
                }
                if (failedTrackers > 0) {
                    // Domain layer uses System.err.println (not android.util.Log) as it's pure Kotlin
                    // System.err.println("Migration completed with $failedTrackers tracker failure(s) out of ${trackerEntries.size} total")
                }
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
                    if (MigrationFlag.CATEGORIES in flags && sourceManga.categoryIds.isNotEmpty()) {
                        sourceManga.categoryIds.forEach { categoryId ->
                            try {
                                categoryRepository.removeMangaFromCategory(sourceManga.id, categoryId)
                            } catch (e: CancellationException) {
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
        } catch (e: CancellationException) {
            throw e
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
        mode: MigrationMode,
        flags: Set<MigrationFlag>
    ): Int {
        var matchedCount = 0

        // Create a map of chapter numbers to source chapters for quick lookup
        val sourceChapterMap = sourceChapters
            .filter { it.chapterNumber >= 0 }
            .associateBy { it.chapterNumber }

        // Source names for download migration
        val fromSourceName = downloadRepository.downloadFolderNameFor(sourceManga.sourceId)
        val fromMangaTitle = sourceManga.title
        val toSourceName = downloadRepository.downloadFolderNameFor(targetManga.sourceId)
        val toMangaTitle = targetManga.title

        // Insert target chapters with matched reading progress
        // L-19: index parameter was unused; replaced with _ and changed to map{}.
        val chaptersToInsert = targetChapters.map { targetChapter ->
            val sourceChapter = sourceChapterMap[targetChapter.chapterNumber]

            if (sourceChapter != null) {
                matchedCount++

                if (MigrationFlag.DOWNLOADS in flags) {
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
            }

            // Reading progress (read/lastPageRead) is only carried over under the CHAPTERS
            // flag; chapter matching itself always happens so downloads above can still find
            // their corresponding source chapter regardless of this flag.
            val carryProgress = MigrationFlag.CHAPTERS in flags && sourceChapter != null

            Chapter(
                id = 0L, // Auto-generate
                mangaId = targetMangaId,
                url = targetChapter.url,
                name = targetChapter.name,
                scanlator = targetChapter.scanlator,
                read = if (carryProgress) sourceChapter?.read ?: false else false,
                lastPageRead = if (carryProgress) sourceChapter?.lastPageRead ?: 0 else 0,
                chapterNumber = targetChapter.chapterNumber,
                dateUpload = targetChapter.dateUpload
            )
        }

        chapterRepository.insertChapters(chaptersToInsert)

        if (MigrationFlag.CHAPTERS in flags) {
            migrateReadingHistory(sourceChapterMap, targetMangaId, targetChapters)
        }

        return matchedCount
    }

    /**
     * Carries each matched chapter's reading history — when it was read, and for how long — onto
     * the target chapter, under the same [MigrationFlag.CHAPTERS] flag that carries read state.
     *
     * Without this the target opens at page one of a chapter marked read, the History screen has
     * nothing to resume from, and the total reading time on the Statistics screen drops by however
     * long the migrated series took. The read flag alone does not carry any of that: it lives on
     * `chapters`, while the timestamps live in `reading_history`, which is keyed by chapter id.
     *
     * That key is why this runs *after* the insert rather than as part of it. The target chapters
     * go in with `id = 0` and Room assigns the real ids, so the only way to know what to write
     * against is to read the rows back. They are matched by `url`, which is the target manga's own
     * unique key for a chapter — chapter number would not do, since it is the *source*'s numbering
     * that was matched on and two target chapters can share a number.
     */
    private suspend fun migrateReadingHistory(
        sourceChapterByNumber: Map<Float, Chapter>,
        targetMangaId: Long,
        targetChapters: List<SourceChapter>,
    ) {
        val historyBySourceChapterId = chapterRepository
            .getHistoryForChapterIds(sourceChapterByNumber.values.map { it.id })
            .associateBy { it.chapterId }
        // Nothing read on the source side means nothing to move, and the read-back below is a query
        // worth skipping for the common case of migrating a series before starting it.
        if (historyBySourceChapterId.isEmpty()) return

        val targetChapterIdByUrl = chapterRepository
            .getChaptersByMangaIdSync(targetMangaId)
            .associate { it.url to it.id }

        // distinctBy the *source* chapter, not the target: a target manga can carry two chapters
        // with the same number — a second scanlation, a re-upload — and both match the same source
        // chapter. Copying the history to each would duplicate the reading time, and the totals on
        // the Statistics screen would climb by a chapter's worth of reading nobody did. That is the
        // same double-counting `replaceHistory` exists to prevent on a re-run, arriving by another
        // route. The read flag legitimately lands on both (they are the same chapter); a duration
        // is a measurement, and it was only spent once.
        val migrated = targetChapters
            .mapNotNull { targetChapter ->
                val sourceChapter = sourceChapterByNumber[targetChapter.chapterNumber]
                    ?: return@mapNotNull null
                val history = historyBySourceChapterId[sourceChapter.id] ?: return@mapNotNull null
                val targetChapterId = targetChapterIdByUrl[targetChapter.url]
                    ?: return@mapNotNull null
                sourceChapter.id to history.copy(chapterId = targetChapterId)
            }
            .distinctBy { (sourceChapterId, _) -> sourceChapterId }
            .map { (_, history) -> history }

        chapterRepository.replaceHistory(migrated)
    }

    private fun MigrationCandidate.toManga(
        favorite: Boolean = false,
        autoDownload: Boolean = false,
        notes: String? = null
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
        autoDownload = autoDownload,
        notes = notes
    )
}
