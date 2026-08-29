package app.otakureader.data.bookmark

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.otakureader.core.network.di.PageImageOkHttp
import app.otakureader.data.download.DownloadProvider
import app.otakureader.domain.bookmark.BookmarkPageExporter
import app.otakureader.domain.model.BookmarkPageRef
import app.otakureader.domain.model.ExportResult
import app.otakureader.domain.model.ShareResult
import app.otakureader.domain.loader.PageLoader
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveSourceId
import app.otakureader.sourceapi.SourceChapter
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves page bookmarks back to their page images and hands them to the gallery or another app.
 *
 * Replaces a placeholder that showed "image export coming in v1.1" on a live button — the
 * canonical mistake CLAUDE.md names under *Never Stub Live UI*.
 *
 * ## Where a page comes from
 *
 * In order: the downloaded page file, a page inside the chapter's CBZ, then the source. The first
 * two are delegated to [PageLoader] rather than reimplemented, because the CBZ path also handles
 * on-demand extraction and passphrase decryption, and a second copy of that would drift.
 *
 * The source leg is what makes this useful. Bookmarking panels while reading online is the case
 * the feature exists for, so an exporter that only handled downloaded chapters would be nearly as
 * empty as the placeholder.
 *
 * ## Two details that are easy to get wrong
 *
 * **The page list is fetched once per chapter, not once per bookmark.** Selecting a run of panels
 * from one chapter is the normal way this gets used, and a per-bookmark fetch would hit the source
 * once for every page saved.
 *
 * **Images are fetched with the [PageImageOkHttp] client, not the plain one.** That client attaches
 * the `Referer` and any per-page headers the source recorded when the page list was fetched;
 * hotlink-protected hosts answer 403 without them. Fetching the page list first is therefore not
 * only about the URL — it is what registers those headers.
 */
@Singleton
class BookmarkPageExporterImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val sourceRepository: SourceRepository,
    private val downloadRepository: DownloadRepository,
    private val pageLoader: PageLoader,
    @param:PageImageOkHttp private val pageImageClient: OkHttpClient,
) : BookmarkPageExporter {

    override suspend fun exportToGallery(pages: List<BookmarkPageRef>): ExportResult =
        withContext(Dispatchers.IO) {
            // Not a failed export — nothing is attempted, and no action by the user changes that.
            // Reporting it as "0 saved" would send them looking for a problem on their device.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext ExportResult.GalleryUnsupported

            var saved = 0
            var failed = 0
            resolveAll(pages).forEach { image ->
                if (image != null && saveToGallery(image)) saved++ else failed++
            }
            ExportResult.Completed(saved = saved, failed = failed)
        }

    override suspend fun prepareForShare(pages: List<BookmarkPageRef>): ShareResult =
        withContext(Dispatchers.IO) {
            val shareDir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            val uris = mutableListOf<String>()
            var failed = 0
            resolveAll(pages).forEach { image ->
                val uri = image?.let { runCatching { copyForShare(it, shareDir) }.getOrNull() }
                if (uri == null) failed++ else uris += uri.toString()
            }
            ShareResult(uris = uris, failed = failed)
        }

    /**
     * Resolves every reference, in the given order, to page bytes — `null` where it could not be
     * found. Grouped by chapter so each chapter's page list is fetched at most once.
     */
    private suspend fun resolveAll(pages: List<BookmarkPageRef>): List<PageImage?> {
        val remoteUrlsByChapter = mutableMapOf<Long, List<String>>()
        return pages.map { ref ->
            try {
                resolve(ref, remoteUrlsByChapter)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun resolve(
        ref: BookmarkPageRef,
        remoteUrlsByChapter: MutableMap<Long, List<String>>,
    ): PageImage? {
        val manga = mangaRepository.getMangaById(ref.mangaId) ?: return null
        val chapter = chapterRepository.getChapterById(ref.chapterId) ?: return null
        val folderName = downloadRepository.downloadFolderNameFor(manga.sourceId)

        localFile(folderName, manga.title, chapter.name, ref.pageIndex)?.let { file ->
            return PageImage(ref, file.readBytes(), extensionOf(file.name))
        }

        val urls = remoteUrlsByChapter.getOrPut(ref.chapterId) {
            fetchPageUrls(manga.sourceId, chapter.url, chapter.name)
        }
        val url = urls.getOrNull(ref.pageIndex)?.takeIf { it.isNotBlank() } ?: return null
        return download(ref, url)
    }

    /**
     * The downloaded file or extracted CBZ page, if either exists.
     *
     * [PageLoader.resolveUrl] returns its `pageUrl` argument unchanged when nothing local is
     * found, so passing an empty one turns it into a lookup: a `file://` answer means a local copy
     * exists, and an empty answer means it does not.
     */
    private fun localFile(folderName: String, mangaTitle: String, chapterName: String, pageIndex: Int): File? {
        val resolved = pageLoader.resolveUrl("", folderName, mangaTitle, chapterName, pageIndex)
        if (resolved == null || !resolved.startsWith(FILE_SCHEME)) return null
        return File(resolved.removePrefix(FILE_SCHEME)).takeIf { it.isFile }
    }

    private suspend fun fetchPageUrls(sourceKey: Long, chapterUrl: String, chapterName: String): List<String> {
        val sourceId = sourceRepository.resolveSourceId(sourceKey) ?: return emptyList()
        return sourceRepository
            .getPageList(sourceId, SourceChapter(url = chapterUrl, name = chapterName))
            .getOrNull()
            ?.map { it.imageUrl.orEmpty() }
            .orEmpty()
    }

    private fun download(ref: BookmarkPageRef, url: String): PageImage? {
        val request = Request.Builder().url(url).build()
        pageImageClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            // Prefer the served content type; a CDN URL often carries no usable extension.
            val extension = response.header("Content-Type")
                ?.substringAfter('/', "")
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it in ALLOWED_EXTENSIONS }
                ?: extensionOf(url.substringBefore('?'))
            return PageImage(ref, bytes, extension)
        }
    }

    private fun saveToGallery(image: PageImage): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, image.fileName)
            put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_DIR")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        var uri: Uri? = null
        return runCatching {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            resolver.openOutputStream(uri!!)?.use { it.write(image.bytes) } ?: return@runCatching false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri!!, values, null, null)
            true
        }.getOrElse {
            // A half-written pending row stays invisible to gallery apps forever otherwise.
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            false
        }
    }

    private fun copyForShare(image: PageImage, shareDir: File): Uri {
        val file = File(shareDir, image.fileName)
        file.writeBytes(image.bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it in ALLOWED_EXTENSIONS } ?: DEFAULT_EXTENSION

    /** A resolved page, held in memory only for as long as it takes to write it out. */
    private data class PageImage(
        val ref: BookmarkPageRef,
        val bytes: ByteArray,
        val extension: String,
    ) {
        val fileName: String
            get() = buildString {
                append(DownloadProvider.sanitize(ref.mangaTitle.ifBlank { "manga" }))
                append('_')
                append(DownloadProvider.sanitize(ref.chapterName.ifBlank { "chapter" }))
                append("_p")
                append(ref.pageIndex + 1)
                append('_')
                append(System.nanoTime())
                append('.')
                append(extension)
            }

        val mimeType: String get() = "image/${if (extension == "jpg") "jpeg" else extension}"

        // Data classes compare arrays by reference; these exist only so the generated equals/
        // hashCode are not quietly wrong. Nothing currently compares a PageImage.
        override fun equals(other: Any?): Boolean =
            this === other || (other is PageImage && ref == other.ref && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = 31 * ref.hashCode() + bytes.contentHashCode()
    }

    private companion object {
        const val FILE_SCHEME = "file://"
        const val SHARE_DIR = "shared_bookmarks"
        const val GALLERY_DIR = "OtakuReader"
        const val DEFAULT_EXTENSION = "jpg"
        val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }
}
