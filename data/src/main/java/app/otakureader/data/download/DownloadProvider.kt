package app.otakureader.data.download

import android.content.Context
import java.io.File

/**
 * Provides filesystem helpers for locally downloaded chapter pages.
 *
 * Directory layout (inside the app-specific external files directory):
 * ```
 * OtakuReader/
 *   {sourceName}/
 *     {mangaTitle}/
 *       {chapterName}/
 *         0.jpg
 *         1.jpg
 *         …
 * ```
 *
 * Using `Context.getExternalFilesDir` means no storage permission is required on
 * any supported Android version.
 *
 * The `Context`-based public API resolves the root directory from the given context.
 * Internal overloads that accept a root `File` directly are provided so that
 * pure-JVM unit tests can exercise the logic without needing an Android Context.
 */
object DownloadProvider {

    private const val ROOT_DIR = "OtakuReader"

    /** The file extensions recognised as downloaded page images. */
    private val PAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    // -------------------------------------------------------------------------
    // Context-based public API
    // -------------------------------------------------------------------------

    /**
     * Returns the directory that holds all pages for [chapterName].
     * The directory may not exist yet; callers are responsible for creating it.
     */
    fun getChapterDir(
        context: Context,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): File = getChapterDir(rootFor(context), sourceName, mangaTitle, chapterName)

    /**
     * Returns the [File] where the page at [pageIndex] should be (or is) stored.
     * Uses `.jpg` as the default extension regardless of the source URL.
     */
    fun getPageFile(
        context: Context,
        sourceName: String,
        mangaTitle: String,
        chapterName: String,
        pageIndex: Int
    ): File = getPageFile(rootFor(context), sourceName, mangaTitle, chapterName, pageIndex)

    /**
     * Returns `true` when the chapter directory exists and contains at least one page file.
     */
    fun isChapterDownloaded(
        context: Context,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): Boolean = isChapterDownloaded(rootFor(context), sourceName, mangaTitle, chapterName)

    /**
     * Returns an ordered list of `file://` URIs for every page that has been
     * downloaded for the given chapter. Pages are sorted by their numeric filename.
     * Returns an empty list if nothing has been downloaded yet.
     */
    fun getDownloadedPageUris(
        context: Context,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): List<String> = getDownloadedPageUris(rootFor(context), sourceName, mangaTitle, chapterName)

    /**
     * Deletes all downloaded files for the given chapter. Returns true if anything was removed.
     */
    fun deleteChapter(
        context: Context,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): Boolean = deleteChapter(rootFor(context), sourceName, mangaTitle, chapterName)

    // -------------------------------------------------------------------------
    // Internal root-File overloads (used for testing without a real Context)
    // -------------------------------------------------------------------------

    internal fun getChapterDir(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): File = File(
        root,
        "$ROOT_DIR/${sanitize(sourceName)}/${sanitize(mangaTitle)}/${sanitize(chapterName)}"
    )

    internal fun getPageFile(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String,
        pageIndex: Int
    ): File = File(getChapterDir(root, sourceName, mangaTitle, chapterName), "$pageIndex.jpg")

    internal fun isChapterDownloaded(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): Boolean {
        val dir = getChapterDir(root, sourceName, mangaTitle, chapterName)
        return dir.isDirectory && dir.listFiles()
            ?.any { it.extension.lowercase() in PAGE_EXTENSIONS } == true
    }

    internal fun getDownloadedPageUris(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): List<String> {
        val dir = getChapterDir(root, sourceName, mangaTitle, chapterName)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension.lowercase() in PAGE_EXTENSIONS }
            ?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }
            ?.map { "file://${it.absolutePath}" }
            ?: emptyList()
    }

    internal fun deleteChapter(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): Boolean {
        val dir = getChapterDir(root, sourceName, mangaTitle, chapterName)
        if (!dir.exists()) return false
        return dir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Replaces characters that are illegal in filesystem paths with underscores and
     * trims surrounding whitespace.
     */
    internal fun sanitize(name: String): String =
        name.replace(Regex("""[/\\:*?"<>|]"""), "_").trim()

    private fun rootFor(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir
}
