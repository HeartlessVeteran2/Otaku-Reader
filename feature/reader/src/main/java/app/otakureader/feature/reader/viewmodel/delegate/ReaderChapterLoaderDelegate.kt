package app.otakureader.feature.reader.viewmodel.delegate

import app.otakureader.domain.loader.PageLoader
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.downloadFolderNameFor
import app.otakureader.domain.repository.resolveSourceId
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.sourceapi.SourceChapter
import javax.inject.Inject

/**
 * Loads chapter metadata, manga metadata and page lists for the reader.
 *
 * Extracted from [app.otakureader.feature.reader.ReaderViewModel]
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
            // An extension can be uninstalled while its manga stay in the library, so this is a
            // normal outcome rather than a fault — same shape as the two lookups above.
            val sourceId = sourceRepository.resolveSourceId(manga.sourceId)
                ?: return Result.NotFound("Source not found")

            val pages = fetchPagesFromSource(
                manga = manga,
                chapter = chapter,
                sourceId = sourceId,
            )
            Result.Success(manga = manga, chapter = chapter, pages = pages)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * Fetch pages from the manga source.
     *
     * For each page, [PageLoader.resolveUrl] is called so that already-downloaded
     * pages are served from local storage rather than the network.
     *
     * @throws Exception if the page list fetch fails. Caller must handle.
     */
    private suspend fun fetchPagesFromSource(
        manga: Manga,
        chapter: Chapter,
        sourceId: String,
    ): List<ReaderPage> {
        // Two different strings, deliberately. [sourceId] addresses the *source* and had to be
        // resolved back from the hashed key on the manga row; `downloadFolderName` addresses the
        // *download directory*. They happened to be the same value while the resolution was
        // broken, which is why one variable was doing both jobs — and why fixing the source
        // lookup alone would have pointed the reader at a folder that does not exist.
        val downloadFolderName = downloadFolderNameFor(manga.sourceId)
        val sourceChapter = SourceChapter(
            url = chapter.url,
            name = chapter.name,
        )
        val pages = sourceRepository.getPageList(sourceId, sourceChapter)
            .getOrElse { throw it }

        return pages.mapIndexed { index, page ->
            ReaderPage(
                index = index,
                imageUrl = pageLoader.resolveUrl(
                    page.imageUrl.orEmpty(),
                    downloadFolderName,
                    manga.title,
                    chapter.name,
                    index,
                ),
                chapterName = chapter.name,
            )
        }
    }
}
