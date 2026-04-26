package app.otakureader.feature.reader.viewmodel.delegate

import app.otakureader.data.loader.PageLoader
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.sourceapi.SourceChapter
import javax.inject.Inject

/**
 * Loads chapter metadata, manga metadata and page lists for the reader.
 *
 * Extracted from [app.otakureader.feature.reader.viewmodel.UltimateReaderViewModel]
 * so that page-loading logic can be tested in isolation.
 */
class ReaderChapterLoaderDelegate @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val sourceRepository: SourceRepository,
    private val pageLoader: PageLoader,
) {

    /**
     * Result of a [load] call. Sealed so the ViewModel can render the
     * appropriate UI state without owning the loading details.
     */
    sealed interface Result {
        data class Success(
            val manga: Manga,
            val chapter: Chapter,
            val pages: List<ReaderPage>,
        ) : Result

        data class NotFound(val message: String) : Result

        data class Failure(val cause: Throwable) : Result
    }

    suspend fun load(mangaId: Long, chapterId: Long): Result {
        return try {
            val chapter = chapterRepository.getChapterById(chapterId)
                ?: return Result.NotFound("Chapter not found")
            val manga = mangaRepository.getMangaById(mangaId)
                ?: return Result.NotFound("Manga not found")

            val pages = fetchPagesFromSource(
                manga = manga,
                chapter = chapter,
            )
            Result.Success(manga = manga, chapter = chapter, pages = pages)
        } catch (e: Exception) {
            // Cancellation must propagate; everything else surfaces as Failure.
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Failure(e)
        }
    }

    /**
     * Fetch pages from the manga source.
     *
     * For each page, [PageLoader.resolveUrl] is called so that already-downloaded
     * pages are served from local storage rather than the network.
     */
    private suspend fun fetchPagesFromSource(
        manga: Manga,
        chapter: Chapter,
    ): List<ReaderPage> {
        val sourceId = manga.sourceId.toString()
        val sourceChapter = SourceChapter(
            url = chapter.url,
            name = chapter.name,
        )
        val pages = sourceRepository.getPageList(sourceId, sourceChapter)
            .getOrElse { return emptyList() }

        return pages.mapIndexed { index, page ->
            ReaderPage(
                index = index,
                imageUrl = pageLoader.resolveUrl(
                    page.imageUrl.orEmpty(),
                    sourceId,
                    manga.title,
                    chapter.name,
                    index,
                ),
                chapterName = chapter.name,
            )
        }
    }
}
