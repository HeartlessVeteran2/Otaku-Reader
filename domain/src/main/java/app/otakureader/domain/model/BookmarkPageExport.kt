package app.otakureader.domain.model

/**
 * Models for [app.otakureader.domain.bookmark.BookmarkPageExporter] (#1132, #1133).
 *
 * They live here rather than beside the interface because `ArchitectureTest` requires every
 * top-level domain data class to be in this package.
 */

/**
 * Identifies one bookmarked page.
 *
 * Deliberately not the bookmark's row id: the caller already holds the resolved bookmark, and
 * re-reading it here would mean a second query for data the screen is displaying.
 */
data class BookmarkPageRef(
    val mangaId: Long,
    val chapterId: Long,
    val pageIndex: Int,
    /** Used to name the exported file; not used to locate the page. */
    val mangaTitle: String,
    val chapterName: String,
)

/** Outcome of [BookmarkPageExporter.exportToGallery]. */
sealed interface ExportResult {

    /**
     * The export ran. [failed] counts pages that could not be resolved or written — an uninstalled
     * extension, a dead source, a network failure — and is reported rather than swallowed, because
     * "12 saved" when 5 silently vanished is the kind of quiet lie this feature already told once.
     */
    data class Completed(val saved: Int, val failed: Int) : ExportResult

    /**
     * The device cannot receive a gallery write at all.
     *
     * Below API 29 a `MediaStore` insert into external storage needs `WRITE_EXTERNAL_STORAGE`,
     * which this app does not request. That is distinct from [Completed] with `saved = 0`: nothing
     * was attempted and nothing the user does will change the result, so the UI has to say so
     * rather than report a failed export.
     */
    data object GalleryUnsupported : ExportResult
}

/** Outcome of [BookmarkPageExporter.prepareForShare]. */
data class ShareResult(
    /** `content://` URIs, in the order the pages were given, for the pages that resolved. */
    val uris: List<String>,
    val failed: Int,
)
