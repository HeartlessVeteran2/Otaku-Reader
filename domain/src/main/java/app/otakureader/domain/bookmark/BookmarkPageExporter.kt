package app.otakureader.domain.bookmark

import app.otakureader.domain.model.BookmarkPageRef
import app.otakureader.domain.model.ExportResult
import app.otakureader.domain.model.ShareResult

/**
 * Turns page bookmarks back into the page images they point at, for saving to the gallery (#1132)
 * or sharing to another app (#1133).
 *
 * A bookmark stores only `(mangaId, chapterId, pageIndex)` — never the image — so every export has
 * to resolve that triple to bytes. The page may be a downloaded file, a page inside a CBZ archive,
 * or nothing local at all, in which case the source has to be asked for the chapter's page list and
 * the image fetched. Implementations must handle all three: a user who bookmarks panels while
 * reading online is the case the feature exists for, and refusing to export those would leave the
 * button almost as useless as the placeholder it replaces.
 *
 * URIs cross this boundary as strings because this module is pure Kotlin and may not import
 * `android.net.Uri`. Callers parse them.
 */
interface BookmarkPageExporter {

    /** Saves each bookmark's page image to the device gallery. */
    suspend fun exportToGallery(pages: List<BookmarkPageRef>): ExportResult

    /**
     * Copies each bookmark's page image somewhere another app can read, returning content URIs.
     *
     * The files land in app cache, not the gallery — sharing should not silently add images to the
     * user's camera roll, and a caller that wants both calls both.
     */
    suspend fun prepareForShare(pages: List<BookmarkPageRef>): ShareResult
}
