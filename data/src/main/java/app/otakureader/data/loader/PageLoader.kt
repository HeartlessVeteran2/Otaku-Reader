package app.otakureader.data.loader

import android.content.Context
import app.otakureader.data.download.DownloadProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the URI that should be used to load a manga page image.
 *
 * When the page has already been downloaded to local storage a `file://` URI is
 * returned instead of the original remote URL, allowing the reader to work
 * completely offline for downloaded chapters.
 */
@Singleton
class PageLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Returns the URI for loading [pageUrl].
     *
     * If a locally downloaded file exists for the given position it returns a
     * `file://` URI pointing to that file; otherwise [pageUrl] is returned
     * unchanged so Coil/OkHttp can fetch it from the network.
     *
     * @param pageUrl     original remote URL for the page image
     * @param sourceName  name of the manga source (e.g. "MangaDex")
     * @param mangaTitle  title of the manga
     * @param chapterName name / title of the chapter
     * @param pageIndex   0-based index of this page within the chapter
     */
    fun resolveUrl(
        pageUrl: String,
        sourceName: String,
        mangaTitle: String,
        chapterName: String,
        pageIndex: Int
    ): String {
        val localFile = DownloadProvider.getPageFile(
            context,
            sourceName,
            mangaTitle,
            chapterName,
            pageIndex
        )
        return if (localFile.exists()) "file://${localFile.absolutePath}" else pageUrl
    }
}
