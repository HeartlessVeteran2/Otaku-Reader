package app.otakureader.data.loader

import android.content.Context
import app.otakureader.core.preferences.CbzEncryptionStore
import app.otakureader.data.download.CbzCreator
import app.otakureader.data.download.DownloadProvider
import app.otakureader.domain.loader.PageLoader as PageLoaderInterface
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
    @param:ApplicationContext private val context: Context,
    private val cbzEncryptionStore: CbzEncryptionStore,
) : PageLoaderInterface {

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
    override fun resolveUrl(
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
        if (localFile.exists()) {
            return "file://${localFile.absolutePath}"
        }

        // Fall back to CBZ extraction if the chapter was archived.
        val cbzFile = DownloadProvider.getCbzFile(
            context,
            sourceName,
            mangaTitle,
            chapterName
        )
        if (cbzFile.exists()) {
            val passphrase = if (CbzCreator.isEncrypted(cbzFile)) cbzEncryptionStore.getPassphrase() else null
            val pageUris = DownloadProvider.getDownloadedPageUris(
                context,
                sourceName,
                mangaTitle,
                chapterName,
                cbzPassphrase = passphrase,
            )
            if (pageIndex < pageUris.size) {
                return pageUris[pageIndex]
            }
        }

        return pageUrl
    }
}
