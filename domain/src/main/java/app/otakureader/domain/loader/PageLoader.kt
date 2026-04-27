package app.otakureader.domain.loader

/**
 * Resolves the effective URL or local path for a manga page.
 *
 * Implementations may check local storage first (downloaded chapters) before
 * falling back to the remote URL, or apply source-specific URL transformations.
 */
interface PageLoader {
    /**
     * Returns the URL or file-path string to load for the given page.
     *
     * @param imageUrl   raw remote URL as returned by the source API (may be blank)
     * @param sourceId   source identifier used as a directory component for local files
     * @param mangaTitle manga title used as a directory component for local files
     * @param chapterName chapter name used as a directory component for local files
     * @param pageIndex  zero-based page index
     * @return resolved URL or local path; null when no URL can be determined
     */
    fun resolveUrl(
        imageUrl: String,
        sourceId: String,
        mangaTitle: String,
        chapterName: String,
        pageIndex: Int,
    ): String?
}
