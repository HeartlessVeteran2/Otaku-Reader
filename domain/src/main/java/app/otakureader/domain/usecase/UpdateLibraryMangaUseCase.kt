package app.otakureader.domain.usecase

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for updating library manga by fetching latest chapters from sources.
 * This will compare fetched chapters with stored chapters and insert any new ones.
 */
class UpdateLibraryMangaUseCase @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val sourceRepository: SourceRepository
) {
    /**
     * Update a single manga by fetching its latest chapters
     * @param manga The manga to update
     * @return Result with number of new chapters found
     */
    suspend operator fun invoke(manga: Manga): Result<Int> {
        return try {
            // Convert domain Manga to SourceManga
            val sourceManga = manga.toSourceManga()

            // Fetch chapter list from source
            val chaptersResult = sourceRepository.getChapterList(
                sourceId = manga.sourceId.toString(),
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
                return Result.success(0) // No new chapters
            }

            // Convert and insert new chapters
            val newChapters = newSourceChapters.map { sourceChapter ->
                sourceChapter.toDomainChapter(mangaId = manga.id)
            }

            chapterRepository.insertChapters(newChapters)

            Result.success(newChapters.size)
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
        bookmark = false,
        lastPageRead = 0,
        chapterNumber = chapterNumber,
        dateUpload = dateUpload
    )
}
