package app.otakureader.data.download

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Utility for creating and reading CBZ (Comic Book ZIP) archives.
 *
 * A CBZ file is a standard ZIP archive containing page image files and an optional
 * `ComicInfo.xml` metadata file. The page files inside the archive are named using
 * the same `{index}.jpg` convention as the loose-file storage layout.
 *
 * The CBZ file is stored inside the chapter directory alongside the loose page files
 * under the fixed name [CBZ_FILE_NAME]:
 * ```
 * OtakuReader/{source}/{manga}/{chapter}/
 *   0.jpg          ← loose page (deleted after CBZ creation in auto mode)
 *   1.jpg
 *   chapter.cbz    ← CBZ archive
 * ```
 */
object CbzCreator {

    /** Fixed filename used for the CBZ archive within each chapter directory. */
    const val CBZ_FILE_NAME = "chapter.cbz"

    /** Image file extensions that are included when packing or unpacking CBZ archives. */
    private val PAGE_EXTENSIONS get() = DownloadProvider.PAGE_EXTENSIONS

    // -------------------------------------------------------------------------
    // Data
    // -------------------------------------------------------------------------

    /**
     * Optional metadata to embed as `ComicInfo.xml` inside the CBZ archive.
     *
     * @param title       Chapter title.
     * @param series      Series / manga title.
     * @param number      Chapter number as a string (e.g. "12" or "12.5").
     * @param writer      Author / writer of the series.
     * @param language    BCP-47 language tag (e.g. "en", "ja").
     */
    data class ComicInfoMetadata(
        val title: String,
        val series: String,
        val number: String? = null,
        val writer: String? = null,
        val language: String? = null
    )

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Packs all page image files inside [chapterDir] into a CBZ archive named
     * [CBZ_FILE_NAME] within that same directory.
     *
     * Optionally embeds a `ComicInfo.xml` entry when [metadata] is provided.
     *
     * The archive is written to a temporary file first and atomically renamed to
     * [CBZ_FILE_NAME] on success, so a failed/interrupted call never leaves a
     * truncated archive behind. If [CBZ_FILE_NAME] already exists it is returned
     * immediately without modification.
     *
     * @param chapterDir directory that contains the downloaded page files.
     * @param metadata   optional comic metadata to embed; `null` skips the XML entry.
     * @return [Result.success] wrapping the created (or existing) CBZ [File] on success,
     *         or [Result.failure] with the cause on any error.
     */
    fun createCbz(chapterDir: File, metadata: ComicInfoMetadata? = null): Result<File> = runCatching {
        require(chapterDir.isDirectory) { "chapterDir must be an existing directory: $chapterDir" }

        // Return the existing archive unchanged – prevents accidentally overwriting
        // CBZ-only chapters that have already had their loose files deleted.
        val cbzFile = File(chapterDir, CBZ_FILE_NAME)
        if (cbzFile.exists()) return@runCatching cbzFile

        val pages = chapterDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in PAGE_EXTENSIONS }
            ?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }
            ?: emptyList()

        require(pages.isNotEmpty()) { "No page images found in $chapterDir; nothing to pack into CBZ" }

        // Write to a temp file so that an error never leaves a corrupt/empty archive.
        val tempFile = File(chapterDir, "$CBZ_FILE_NAME.tmp")
        try {
            ZipOutputStream(tempFile.outputStream().buffered()).use { zos ->
                metadata?.let {
                    zos.putNextEntry(ZipEntry("ComicInfo.xml"))
                    zos.write(buildComicInfoXml(it).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                pages.forEach { page ->
                    zos.putNextEntry(ZipEntry(page.name))
                    page.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            // Atomic rename: replaces any stale temp file atomically.
            tempFile.renameTo(cbzFile)
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
        cbzFile
    }

    /**
     * Extracts all page image files from [cbzFile] into [destDir].
     *
     * Only entries whose extensions are in [PAGE_EXTENSIONS] are extracted;
     * metadata files such as `ComicInfo.xml` are skipped.
     *
     * @param cbzFile path to the `.cbz` archive.
     * @param destDir directory to which page files are written.
     * @return [Result.success] wrapping an ordered list of extracted [File]s on success,
     *         or [Result.failure] on any error.
     */
    fun extractCbzPages(cbzFile: File, destDir: File): Result<List<File>> = runCatching {
        destDir.mkdirs()
        val extracted = mutableListOf<File>()
        ZipFile(cbzFile).use { zip ->
            zip.entries().asSequence()
                .filter { entry ->
                    !entry.isDirectory &&
                        entry.name.substringAfterLast('.').lowercase() in PAGE_EXTENSIONS
                }
                .sortedBy { entry ->
                    entry.name.substringAfterLast('/').substringBeforeLast('.').toIntOrNull()
                        ?: Int.MAX_VALUE
                }
                .forEach { entry ->
                    val safeName = entry.name.replace('/', '_').replace('\\', '_').trimStart('.')
                    val outFile = File(destDir, safeName)
                    // Guard against path traversal: ensure the resolved path stays within destDir
                    if (!outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                        return@forEach
                    }
                    zip.getInputStream(entry).use { input -> outFile.outputStream().use { input.copyTo(it) } }
                    extracted += outFile
                }
        }
        extracted.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    fun buildComicInfoXml(metadata: ComicInfoMetadata): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">""")
        appendLine("""  <Title>${escapeXml(metadata.title)}</Title>""")
        appendLine("""  <Series>${escapeXml(metadata.series)}</Series>""")
        metadata.number?.let { appendLine("""  <Number>${escapeXml(it)}</Number>""") }
        metadata.writer?.let { appendLine("""  <Writer>${escapeXml(it)}</Writer>""") }
        metadata.language?.let { appendLine("""  <LanguageISO>${escapeXml(it)}</LanguageISO>""") }
        append("""</ComicInfo>""")
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
