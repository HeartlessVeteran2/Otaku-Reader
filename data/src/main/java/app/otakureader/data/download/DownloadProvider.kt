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
 *         chapter.cbz   ← optional CBZ archive
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

    /** Maximum number of page files to list per chapter for safety. */
    private const val MAX_PAGE_FILES = 1000

    /**
     * Subdirectory within a chapter directory used as a cache for pages
     * extracted on-demand from a CBZ archive. Keeping them in a dedicated
     * subdirectory prevents them from being mistaken for original loose files.
     */
    private const val PAGES_CACHE_SUBDIR = ".pages"

    /** The file extensions recognised as downloaded page images. */
    val PAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

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
     * Returns the [File] path for the CBZ archive of [chapterName].
     * The file may not exist yet.
     */
    fun getCbzFile(
        context: Context,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): File = getCbzFile(rootFor(context), sourceName, mangaTitle, chapterName)

    /**
     * Returns `true` when the chapter directory exists and contains at least one page file
     * or a CBZ archive.
     */
    fun isChapterDownloaded(
        context: Context,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): Boolean = isChapterDownloaded(rootFor(context), sourceName, mangaTitle, chapterName)

    /**
     * Returns `true` when the manga directory exists and contains at least one
     * chapter subdirectory with downloaded pages. This is a coarse check suitable
     * for library-level "downloaded" badges without per-chapter iteration.
     */
    fun hasMangaDownloads(
        context: Context,
        sourceName: String,
        mangaTitle: String
    ): Boolean = hasMangaDownloads(rootFor(context), sourceName, mangaTitle)

    /**
     * Returns an ordered list of `file://` URIs for every page that has been
     * downloaded for the given chapter. Pages are sorted by their numeric filename.
     *
     * When the chapter was saved as a CBZ archive and no loose page files exist,
     * the archive is extracted into the chapter directory on demand and those file
     * URIs are returned.
     *
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

    /**
     * Migrates downloaded chapter files from one location to another.
     * Used during manga migration to preserve downloads when moving between sources.
     *
     * @param context Android context for filesystem access
     * @param fromSourceName Source name of the original manga
     * @param fromMangaTitle Manga title of the original manga
     * @param fromChapterName Chapter name in the original manga
     * @param toSourceName Source name of the target manga
     * @param toMangaTitle Manga title of the target manga
     * @param toChapterName Chapter name in the target manga
     * @param copy If true, copies files (COPY mode). If false, moves files (MOVE mode)
     * @return true if migration was successful, false if no files to migrate or migration failed
     */
    fun migrateChapterDownload(
        context: Context,
        fromSourceName: String,
        fromMangaTitle: String,
        fromChapterName: String,
        toSourceName: String,
        toMangaTitle: String,
        toChapterName: String,
        copy: Boolean = false
    ): Boolean = migrateChapterDownload(
        rootFor(context),
        fromSourceName,
        fromMangaTitle,
        fromChapterName,
        toSourceName,
        toMangaTitle,
        toChapterName,
        copy
    )

    // -------------------------------------------------------------------------
    // Internal root-File overloads (used for testing without a real Context)
    // -------------------------------------------------------------------------

    fun getChapterDir(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): File = File(
        root,
        "$ROOT_DIR/${sanitize(sourceName)}/${sanitize(mangaTitle)}/${sanitize(chapterName)}"
    )

    fun getPageFile(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String,
        pageIndex: Int
    ): File = File(getChapterDir(root, sourceName, mangaTitle, chapterName), "$pageIndex.jpg")

    fun getCbzFile(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): File = File(getChapterDir(root, sourceName, mangaTitle, chapterName), CbzCreator.CBZ_FILE_NAME)

    fun isChapterDownloaded(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): Boolean {
        val dir = getChapterDir(root, sourceName, mangaTitle, chapterName)
        if (!dir.isDirectory) return false

        // Use list() instead of listFiles() for better performance and null safety
        val fileList = dir.list() ?: return false
        return fileList.take(MAX_PAGE_FILES).any { filename ->
            filename == CbzCreator.CBZ_FILE_NAME ||
                filename.substringAfterLast('.', "").lowercase() in PAGE_EXTENSIONS
        }
    }

    /**
     * Returns `true` when the manga directory contains at least one chapter subdirectory
     * with at least one downloaded page file or CBZ archive.
     */
    fun hasMangaDownloads(
        root: File,
        sourceName: String,
        mangaTitle: String
    ): Boolean {
        val mangaDir = File(root, "$ROOT_DIR/${sanitize(sourceName)}/${sanitize(mangaTitle)}")
        if (!mangaDir.isDirectory) return false

        val chapterDirs = mangaDir.listFiles { file -> file.isDirectory } ?: return false
        return chapterDirs.any { chapterDir ->
            val fileList = chapterDir.list() ?: return@any false
            fileList.any { filename ->
                filename == CbzCreator.CBZ_FILE_NAME ||
                    filename.substringAfterLast('.', "").lowercase() in PAGE_EXTENSIONS
            }
        }
    }

    fun getDownloadedPageUris(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): List<String> {
        val dir = getChapterDir(root, sourceName, mangaTitle, chapterName)
        if (!dir.isDirectory) return emptyList()

        // Prefer loose page files at the chapter directory level (backward-compatible).
        val files = dir.listFiles() ?: return emptyList()
        val looseFiles = files
            .asSequence()
            .filter { it.isFile && it.extension.lowercase() in PAGE_EXTENSIONS }
            .sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }
            .take(MAX_PAGE_FILES)
            .toList()

        if (looseFiles.isNotEmpty()) {
            return looseFiles.map { "file://${it.absolutePath}" }
        }

        // Fall back to CBZ: extract pages on demand into a dedicated subdirectory so
        // they are never confused with original loose-file downloads. The subdirectory
        // is re-used on subsequent reads (no re-extraction if the cache is present).
        val cbzFile = File(dir, CbzCreator.CBZ_FILE_NAME)
        if (!cbzFile.exists()) return emptyList()

        val cacheDir = File(dir, PAGES_CACHE_SUBDIR)
        val cachedFiles = if (cacheDir.isDirectory) {
            cacheDir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in PAGE_EXTENSIONS }
                ?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }
                ?: emptyList()
        } else emptyList()

        if (cachedFiles.isNotEmpty()) {
            return cachedFiles.take(MAX_PAGE_FILES).map { "file://${it.absolutePath}" }
        }

        // Extract CBZ pages into the cache subdirectory for this and future reads.
        val extracted = CbzCreator.extractCbzPages(cbzFile, cacheDir).getOrNull()
            ?: return emptyList()
        return extracted.take(MAX_PAGE_FILES).map { "file://${it.absolutePath}" }
    }

    fun deleteChapter(
        root: File,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): Boolean {
        val dir = getChapterDir(root, sourceName, mangaTitle, chapterName)
        if (!dir.exists()) return false
        return dir.deleteRecursively()
    }

    fun migrateChapterDownload(
        root: File,
        fromSourceName: String,
        fromMangaTitle: String,
        fromChapterName: String,
        toSourceName: String,
        toMangaTitle: String,
        toChapterName: String,
        copy: Boolean = false
    ): Boolean {
        val fromDir = getChapterDir(root, fromSourceName, fromMangaTitle, fromChapterName)
        val toDir = getChapterDir(root, toSourceName, toMangaTitle, toChapterName)

        // Nothing to migrate if source directory doesn't exist or has no files
        if (!fromDir.isDirectory) return false
        val files = fromDir.listFiles() ?: return false
        if (files.isEmpty()) return false

        // Create destination directory
        toDir.mkdirs()

        // Copy or move all files from source to destination.
        // renameTo() can fail across different filesystems/volumes, so we fall
        // back to copy-then-delete when a rename returns false.
        return try {
            files.forEach { file ->
                if (file.isFile) {
                    val destFile = File(toDir, file.name)
                    if (copy) {
                        file.copyTo(destFile, overwrite = true)
                    } else {
                        if (!file.renameTo(destFile)) {
                            // Fallback: copy then delete original
                            file.copyTo(destFile, overwrite = true)
                            // Verify the copy succeeded before deleting
                            if (!destFile.exists() || destFile.length() != file.length()) {
                                // Copy verification failed; abort to avoid data loss
                                // Clean up partial destination file best-effort
                                destFile.delete()
                                return false
                            }
                            if (!file.delete()) {
                                // Delete failed; migration incomplete
                                return false
                            }
                        }
                    }
                } else if (file.isDirectory) {
                    // Handle subdirectories (e.g., .pages cache)
                    val destSubdir = File(toDir, file.name)
                    if (copy) {
                        file.copyRecursively(destSubdir, overwrite = true)
                    } else {
                        if (!file.renameTo(destSubdir)) {
                            // Fallback: copy recursively then delete original
                            // Use try-catch to detect partial copy failures
                            try {
                                file.copyRecursively(destSubdir, overwrite = true)
                                // Verify destination directory was created with contents
                                if (!destSubdir.isDirectory || destSubdir.listFiles().isNullOrEmpty()) {
                                    // Copy verification failed; abort to avoid data loss
                                    // Clean up partial copy
                                    destSubdir.deleteRecursively()
                                    return false
                                }
                            } catch (e: Exception) {
                                // Copy failed; clean up partial copy and abort
                                destSubdir.deleteRecursively()
                                return false
                            }
                            // Copy verified; now safe to delete original
                            if (!file.deleteRecursively()) {
                                // Delete failed; migration incomplete
                                return false
                            }
                        }
                    }
                }
            }

            // If moving (not copying), delete the now-empty source directory
            if (!copy) {
                fromDir.deleteRecursively()
            }

            true
        } catch (e: Exception) {
            // Migration failed, but don't throw - just return false
            false
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Replaces characters that are illegal in filesystem paths with underscores and
     * trims surrounding whitespace.
     */
    fun sanitize(name: String): String =
        name.replace(Regex("""[/\\:*?"<>|]"""), "_").trim()

    private fun rootFor(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir
}
