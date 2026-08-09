package app.otakureader.domain.usecase

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveSourceId
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for updating library manga by fetching latest chapters from sources.
 * This will compare fetched chapters with stored chapters and insert any new ones.
 */
class UpdateLibraryMangaUseCase @Inject constructor(
    private val chapterRepository: ChapterRepository,
    private val sourceRepository: SourceRepository
) {
    /**
     * Update a single manga by fetching its latest chapters.
     *
     * Returns the new chapters rather than a count. The count was all any caller needed until the
     * feed needed to *name* what arrived, and the chapters were already sitting here — they were
     * computed, inserted, and then discarded in favour of `.size`.
     *
     * @param manga The manga to update
     * @return Result with the chapters that were new, empty when there were none
     */
    suspend operator fun invoke(manga: Manga): Result<List<Chapter>> {
        return try {
            // Convert domain Manga to SourceManga
            val sourceManga = manga.toSourceManga()

            // `manga.sourceId` is a hash of the source's string id, so it has to be resolved back
            // through the loaded sources — stringifying it yields the hash's decimal, which
            // matches no source and made every library update fail with "Source not found".
            val sourceId = sourceRepository.resolveSourceId(manga.sourceId)
                ?: return Result.failure(
                    IllegalStateException("Source not found for manga ${manga.id}")
                )

            // Fetch chapter list from source
            val chaptersResult = sourceRepository.getChapterList(
                sourceId = sourceId,
                manga = sourceManga
            )

            if (chaptersResult.isFailure) {
                return Result.failure(
                    chaptersResult.exceptionOrNull() ?: Exception("Failed to fetch chapters")
                )
            }

            val sourceChapters = chaptersResult.getOrNull() ?: emptyList()

            // Get existing chapters from database (get first emission)
            val existingChapters = chapterRepository.getChaptersByMangaId(manga.id).first()

            // Find new chapters (chapters from source not in database)
            val existingUrls = existingChapters.map { it.url }.toSet()
            val newSourceChapters = sourceChapters.filter { it.url !in existingUrls }

            if (newSourceChapters.isEmpty()) {
                return Result.success(emptyList())
            }

            // Convert and insert new chapters
            val newChapters = newSourceChapters.map { sourceChapter ->
                sourceChapter.toDomainChapter(mangaId = manga.id)
            }

            chapterRepository.insertChapters(newChapters)

            // Re-read to get the chapters as stored. The ones built above carry `id = 0` because
            // Room assigns the id on insert, and a caller that wants to link to a chapter — the
            // feed does — needs the real one. Only pays for a read when something was new.
            val newUrls = newSourceChapters.mapTo(mutableSetOf()) { it.url }
            val stored = chapterRepository.getChaptersByMangaId(manga.id).first()
                .filter { it.url in newUrls }

            // Falling back to the pre-insert list keeps the count honest if the re-read comes back
            // short: the chapters were inserted either way, and reporting none would tell the
            // caller nothing happened.
            Result.success(stored.ifEmpty { newChapters })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Manga.toSourceManga() = SourceManga(
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        description = description,
        author = author,
        artist = artist,
        genre = genre.joinToString(", "),
        status = status.ordinal,
        initialized = initialized
    )

    private fun SourceChapter.toDomainChapter(mangaId: Long) = Chapter(
        id = 0L, // Room will auto-generate
        mangaId = mangaId,
        url = url,
        name = name,
        scanlator = scanlator,
        read = false,
        lastPageRead = 0,
        chapterNumber = chapterNumber,
        dateUpload = dateUpload,
        dateFetch = System.currentTimeMillis()
    )
}
