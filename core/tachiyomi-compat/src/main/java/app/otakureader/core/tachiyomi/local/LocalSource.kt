package app.otakureader.core.tachiyomi.local

import android.content.Context
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.Page
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Local manga source that serves manga from CBZ, ZIP, EPUB files and plain image folders
 * stored on-device under a user-configurable directory.
 *
 * Directory layout expected:
 * ```
 * <localDirectory>/
 *   MangaTitle/                    ← folder-based manga
 *     chapter-01/
 *       001.jpg
 *       002.jpg
 *     chapter-02.cbz
 *   AnotherManga.cbz               ← single-archive manga (one-shot)
 *   SomeManga.epub                 ← EPUB comic
 * ```
 *
 * Supported formats for chapters:
 * - Directories containing image files (jpg, jpeg, png, gif, webp)
 * - CBZ / ZIP archives
 * - EPUB files (images extracted from the reading spine)
 *
 * Metadata sources (in priority order, per-format):
 * - Folder-based manga: `series.json` → `ComicInfo.xml` (from first archive chapter) → directory name
 * - CBZ/ZIP archives: `ComicInfo.xml` (embedded) → filename
 * - EPUB files: OPF metadata → filename
 */
class LocalSource(
    private val context: Context,
    private val directory: String
) : MangaSource {

    override val id: String = "local"
    override val name: String = "Local"
    override val lang: String = "all"
    override val baseUrl: String = ""
    override val supportsLatest: Boolean = false

    // ─── Supported file extensions ────────────────────────────────────

    private val archiveExtensions = setOf("cbz", "zip", "epub")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif")

    // Canonical base directory resolved once; null if the path cannot be canonicalized.
    private val baseDirectory: File? = try { File(directory).canonicalFile } catch (_: Exception) { null }

    // ─── Directory scanning ───────────────────────────────────────────

    private fun scanDirectory(): List<LocalMangaEntry> {
        val root = File(directory)
        if (!root.exists() || !root.isDirectory) return emptyList()

        return buildList {
            root.listFiles()?.forEach { entry ->
                when {
                    // Sub-directory → manga with one or more chapters
                    entry.isDirectory -> add(LocalMangaEntry.Folder(entry))
                    // CBZ/ZIP/EPUB at root level → one-shot manga
                    entry.isFile && entry.extension.lowercase() in archiveExtensions ->
                        add(LocalMangaEntry.Archive(entry))
                }
            }
        }.sortedBy { it.name.lowercase() }
    }

    // ─── MangaSource ─────────────────────────────────────────────────

    override suspend fun fetchPopularManga(page: Int): MangaPage = withContext(Dispatchers.IO) {
        val entries = scanDirectory()
        val mangas = entries.map { entry -> buildSourceManga(entry) }
        MangaPage(mangas = mangas, hasNextPage = false)
    }

    override suspend fun fetchLatestUpdates(page: Int): MangaPage = fetchPopularManga(page)

    override suspend fun fetchSearchManga(page: Int, query: String, filters: FilterList): MangaPage =
        withContext(Dispatchers.IO) {
            val all = scanDirectory()
            val filtered = if (query.isBlank()) all
            else all.filter { it.name.contains(query, ignoreCase = true) }
            MangaPage(mangas = filtered.map { buildSourceManga(it) }, hasNextPage = false)
        }

    override suspend fun fetchMangaDetails(manga: SourceManga): SourceManga =
        withContext(Dispatchers.IO) {
            val entry = findEntryByUrl(manga.url) ?: return@withContext manga
            buildSourceManga(entry)
        }

    override suspend fun fetchChapterList(manga: SourceManga): List<SourceChapter> =
        withContext(Dispatchers.IO) {
            val entry = findEntryByUrl(manga.url) ?: return@withContext emptyList()
            buildChapterList(entry)
        }

    override suspend fun fetchPageList(chapter: SourceChapter): List<Page> =
        withContext(Dispatchers.IO) {
            resolvePages(chapter.url)
        }

    // ─── Helpers — metadata ───────────────────────────────────────────

    private fun buildSourceManga(entry: LocalMangaEntry): SourceManga {
        return when (entry) {
            is LocalMangaEntry.Folder -> {
                val meta = readSeriesJson(entry.file)
                    ?: readComicInfoFromFirstChapter(entry.file)
                SourceManga(
                    url = entry.file.absolutePath,
                    title = meta?.title ?: entry.name,
                    thumbnailUrl = findCoverImage(entry.file),
                    description = meta?.description,
                    author = meta?.author,
                    artist = meta?.artist,
                    initialized = true
                )
            }
            is LocalMangaEntry.Archive -> {
                val meta = when (entry.file.extension.lowercase()) {
                    "epub" -> readEpubMeta(entry.file)
                    else -> readComicInfoFromArchive(entry.file)
                }
                SourceManga(
                    url = entry.file.absolutePath,
                    title = meta?.title ?: entry.name,
                    thumbnailUrl = null,
                    description = meta?.description,
                    author = meta?.author,
                    artist = meta?.artist,
                    initialized = true
                )
            }
        }
    }

    private fun findCoverImage(folder: File): String? {
        // Check direct cover files first
        for (name in listOf("cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.png")) {
            val f = File(folder, name)
            if (f.exists()) return "file://${f.absolutePath}"
        }
        // Fall back to the first image inside the first chapter folder or archive
        folder.listFiles()
            ?.sortedBy { it.name.lowercase() }
            ?.firstOrNull()
            ?.let { first ->
                return when {
                    first.isDirectory -> firstImageInFolder(first)?.let { "file://$it" }
                    else -> null
                }
            }
        return null
    }

    private fun firstImageInFolder(folder: File): String? =
        folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in imageExtensions }
            ?.minByOrNull { it.name }
            ?.absolutePath

    // ─── Helpers — chapter listing ────────────────────────────────────

    private fun buildChapterList(entry: LocalMangaEntry): List<SourceChapter> {
        return when (entry) {
            is LocalMangaEntry.Archive -> {
                // Single archive → one chapter
                listOf(
                    SourceChapter(
                        url = chapterUrl(entry.file.absolutePath, null),
                        name = entry.name,
                        chapterNumber = 1f,
                        dateUpload = entry.file.lastModified()
                    )
                )
            }
            is LocalMangaEntry.Folder -> {
                val chapters = mutableListOf<SourceChapter>()
                entry.file.listFiles()
                    ?.sortedBy { it.name.lowercase() }
                    ?.forEachIndexed { index, child ->
                        when {
                            child.isDirectory && containsImages(child) ->
                                chapters += SourceChapter(
                                    url = chapterUrl(entry.file.absolutePath, child.name),
                                    name = child.name,
                                    chapterNumber = (chapters.size + 1).toFloat(),
                                    dateUpload = child.lastModified()
                                )
                            child.isFile && child.extension.lowercase() in archiveExtensions ->
                                chapters += SourceChapter(
                                    url = chapterUrl(entry.file.absolutePath, child.name),
                                    name = child.nameWithoutExtension,
                                    chapterNumber = (chapters.size + 1).toFloat(),
                                    dateUpload = child.lastModified()
                                )
                        }
                    }
                // If no sub-chapters found, treat folder itself as a single chapter
                if (chapters.isEmpty() && containsImages(entry.file)) {
                    chapters += SourceChapter(
                        url = chapterUrl(entry.file.absolutePath, null),
                        name = entry.name,
                        chapterNumber = 1f,
                        dateUpload = entry.file.lastModified()
                    )
                }
                chapters.reversed() // newest first
            }
        }
    }

    private fun containsImages(folder: File): Boolean =
        folder.listFiles()?.any { it.isFile && it.extension.lowercase() in imageExtensions } == true

    // ─── Helpers — page resolution ────────────────────────────────────

    /**
     * Returns `true` if [file] (after canonical resolution) is equal to or a descendant of
     * the configured [directory]. Used to guard against path-traversal via crafted URLs.
     */
    private fun isWithinBaseDirectory(file: File): Boolean {
        val baseDir = baseDirectory ?: return false
        val target = try { file.canonicalFile } catch (e: Exception) { return false }
        val basePath = baseDir.absolutePath
        val targetPath = target.absolutePath
        return target == baseDir ||
            (targetPath.startsWith(basePath) &&
                (targetPath.length == basePath.length ||
                    targetPath[basePath.length] == File.separatorChar))
    }

    /**
     * Resolves a chapter URL (produced by [chapterUrl]) into a list of [Page] objects
     * pointing to local file URIs so the image loader can display them without network.
     */
    private suspend fun resolvePages(chapterUrl: String): List<Page> = withContext(Dispatchers.IO) {
        val (mangaPath, chapterName) = parseChapterUrl(chapterUrl)
        val mangaFile = File(mangaPath)

        // Guard against path-traversal via crafted chapter URLs — this method is called
        // from the public fetchPageList override which bypasses findEntryByUrl.
        if (!isWithinBaseDirectory(mangaFile)) return@withContext emptyList()

        when {
            // Manga is itself a single archive file
            !mangaFile.isDirectory -> {
                when (mangaFile.extension.lowercase()) {
                    "epub" -> extractEpubPages(mangaFile)
                    else -> extractArchivePages(mangaFile)
                }
            }
            chapterName == null -> {
                // Folder manga, no sub-chapter — images directly in folder
                imagesInFolder(mangaFile)
            }
            else -> {
                val chapterFile = File(mangaFile, chapterName)
                // Also validate the sub-chapter path (defense-in-depth for crafted chapterName)
                if (!isWithinBaseDirectory(chapterFile)) return@withContext emptyList()
                when {
                    chapterFile.isDirectory -> imagesInFolder(chapterFile)
                    chapterFile.extension.lowercase() == "epub" -> extractEpubPages(chapterFile)
                    chapterFile.extension.lowercase() in setOf("cbz", "zip") ->
                        extractArchivePages(chapterFile)
                    else -> emptyList()
                }
            }
        }
    }

    private fun imagesInFolder(folder: File): List<Page> =
        (folder.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase() in imageExtensions }
            .sortedWith(NATURAL_FILENAME_ORDER)
            .mapIndexed { index, file ->
                Page(index = index, url = "", imageUrl = "file://${file.absolutePath}")
            }

    // ─── CBZ / ZIP extraction ─────────────────────────────────────────

    private fun extractArchivePages(archive: File): List<Page> {
        if (!archive.exists()) return emptyList()
        return try {
            // Use a hash of the full absolute path to avoid name collisions between
            // archives with the same filename in different parent directories.
            val cacheKey = archive.absolutePath.hashCode().toUInt().toString(16)
            val cacheDir = File(context.cacheDir, "local_source/$cacheKey")
            cacheDir.mkdirs()

            ZipFile(archive).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in imageExtensions }
                    .sortedWith(compareBy(NATURAL_STRING_ORDER) { it.name })
                    .mapIndexed { index, entry ->
                        // Sanitize: flatten any directory structure to prevent path traversal
                        val safeName = entry.name.replace('/', '_').replace('\\', '_')
                            .trimStart('.')
                        val outFile = File(cacheDir, safeName)
                        // Guard against path traversal attacks
                        if (!outFile.canonicalPath.startsWith(cacheDir.canonicalPath + File.separator)) {
                            return@mapIndexed null
                        }
                        if (!outFile.exists()) {
                            zip.getInputStream(entry).use { input ->
                                outFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        Page(index = index, url = "", imageUrl = "file://${outFile.absolutePath}")
                    }
                    .filterNotNull()
                    .toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─── ComicInfo.xml parsing ────────────────────────────────────────

    private fun readComicInfoFromArchive(archive: File): LocalMeta? {
        if (!archive.exists()) return null
        return try {
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry("ComicInfo.xml") ?: return null
                parseComicInfo(zip.getInputStream(entry))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readComicInfoFromFirstChapter(folder: File): LocalMeta? {
        val firstArchive = folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("cbz", "zip") }
            ?.minByOrNull { it.name }
            ?: return null
        return readComicInfoFromArchive(firstArchive)
    }

    private fun parseComicInfo(stream: InputStream): LocalMeta? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(stream, null)

            var title: String? = null
            var author: String? = null
            var artist: String? = null
            var description: String? = null

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "Series" -> title = parser.nextText()
                        "Writer" -> author = parser.nextText()
                        "Penciller", "Artist" -> if (artist == null) artist = parser.nextText()
                        "Summary" -> description = parser.nextText()
                    }
                }
                event = parser.next()
            }
            LocalMeta(title = title, author = author, artist = artist, description = description)
        } catch (e: Exception) {
            null
        }
    }

    // ─── series.json parsing ──────────────────────────────────────────

    private fun readSeriesJson(folder: File): LocalMeta? {
        val jsonFile = File(folder, "series.json")
        if (!jsonFile.exists()) return null
        return try {
            val text = jsonFile.readText()
            // Minimal key-value extraction without a full JSON library
            fun extract(key: String): String? {
                val pattern = Regex(""""$key"\s*:\s*"([^"]*?)"""")
                return pattern.find(text)?.groupValues?.get(1)
            }
            LocalMeta(
                title = extract("title"),
                author = extract("author"),
                artist = extract("artist"),
                description = extract("description") ?: extract("summary")
            )
        } catch (e: Exception) {
            null
        }
    }

    // ─── EPUB parsing ─────────────────────────────────────────────────

    private fun readEpubMeta(epub: File): LocalMeta? {
        if (!epub.exists()) return null
        return try {
            ZipFile(epub).use { zip ->
                val opfEntry = zip.entries().asSequence()
                    .firstOrNull { it.name.endsWith(".opf") }
                    ?: return null
                parseOpfMeta(zip.getInputStream(opfEntry))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOpfMeta(stream: InputStream): LocalMeta? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, null)

            var title: String? = null
            var author: String? = null
            var description: String? = null
            var inMetadata = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name.substringAfterLast(':')
                        when (localName) {
                            "metadata" -> inMetadata = true
                            "title" -> if (inMetadata && title == null) title = parser.nextText()
                            "creator" -> if (inMetadata && author == null) author = parser.nextText()
                            "description" -> if (inMetadata && description == null) description = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.substringAfterLast(':') == "metadata") inMetadata = false
                    }
                }
                event = parser.next()
            }
            LocalMeta(title = title, author = author, description = description)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractEpubPages(epub: File): List<Page> {
        if (!epub.exists()) return emptyList()
        return try {
            // Use a hash of the full absolute path to avoid name collisions between
            // EPUB files with the same filename in different parent directories.
            val cacheKey = epub.absolutePath.hashCode().toUInt().toString(16)
            val cacheDir = File(context.cacheDir, "local_source/$cacheKey")
            cacheDir.mkdirs()

            ZipFile(epub).use { zip ->
                // Find the OPF to get the reading order
                val opfEntry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf") }
                val spineHrefs: List<String> = if (opfEntry != null) {
                    parseOpfSpineImagePaths(zip.getInputStream(opfEntry))
                } else {
                    emptyList()
                }

                // Collect all image entries
                val imageEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in imageExtensions }
                    .associateBy { it.name }

                // Build ordered list of image zip entries:
                //  1. For each spine href, try direct match (image-only EPUB) then basename
                //     match (XHTML-wrapped manga EPUB where page001.xhtml → page001.jpg).
                //  2. Fall back to natural alphabetical order of all images.
                val sortedEntries: List<java.util.zip.ZipEntry> = when {
                    spineHrefs.isEmpty() ->
                        imageEntries.values.sortedWith(compareBy(NATURAL_STRING_ORDER) { it.name })
                    else -> {
                        val ordered = spineHrefs.mapNotNull { href ->
                            val filename = href.substringAfterLast('/')
                            val baseName = filename.substringBeforeLast('.')
                            // Direct image match (image-only EPUB)
                            imageEntries.entries.firstOrNull { (key, _) ->
                                key.endsWith(href) || key.endsWith(filename)
                            }?.value
                            // XHTML-wrapper pattern: page001.xhtml → page001.jpg (same basename)
                                ?: imageEntries.entries.firstOrNull { (_, entry) ->
                                    val entryBaseName = entry.name
                                        .substringAfterLast('/')
                                        .substringBeforeLast('.')
                                    entryBaseName == baseName
                                }?.value
                        }.distinctBy { it.name } // remove duplicates if two spine items map to same image

                        ordered.ifEmpty {
                            imageEntries.values.sortedWith(compareBy(NATURAL_STRING_ORDER) { it.name })
                        }
                    }
                }

                sortedEntries.mapIndexed { index, entry ->
                    val ext = entry.name.substringAfterLast('.').lowercase()
                    val outFile = File(cacheDir, "page_${index.toString().padStart(4, '0')}.$ext")
                    // Guard: outFile must stay inside cacheDir
                    if (!outFile.canonicalPath.startsWith(cacheDir.canonicalPath)) {
                        return@mapIndexed null
                    }
                    if (!outFile.exists()) {
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    Page(index = index, url = "", imageUrl = "file://${outFile.absolutePath}")
                }.filterNotNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse content.opf and return image paths in spine reading order.
     *
     * Standard EPUB structure:
     * - The manifest lists BOTH XHTML content documents and image items.
     * - The spine lists XHTML content documents (by id), not images directly.
     * - Each XHTML page typically contains one image via `<img src="...">` or
     *   a CSS background referencing the image.
     *
     * For manga EPUBs that use the XHTML-wrapping pattern, we resolve each spine
     * item's href and then look for images referenced from that XHTML document.
     * For image-only EPUBs (non-standard), we return image manifest items directly.
     */
    private fun parseOpfSpineImagePaths(stream: InputStream): List<String> {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, null)

            // manifest: id → ManifestItem(href, mediaType)
            val manifest = mutableMapOf<String, ManifestItem>()
            // spine: ordered list of idref
            val spineIdRefs = mutableListOf<String>()
            var inSpine = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name.substringAfterLast(':')
                        when (localName) {
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                                if (id != null && href != null) {
                                    manifest[id] = ManifestItem(href, mediaType)
                                }
                            }
                            "spine" -> inSpine = true
                            "itemref" -> if (inSpine) {
                                parser.getAttributeValue(null, "idref")?.let { spineIdRefs += it }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.substringAfterLast(':') == "spine") inSpine = false
                    }
                }
                event = parser.next()
            }

            // Collect direct image items and XHTML item hrefs from the spine.
            // Manga EPUBs typically wrap each image in an XHTML page; we return
            // the XHTML hrefs here so the caller can match them back to the ZIP
            // entries. Images referenced directly in the spine (non-standard) are
            // also included.
            val imageMediaTypes = setOf("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp")
            val xhtmlMediaTypes = setOf("application/xhtml+xml", "text/html")

            if (spineIdRefs.isNotEmpty()) {
                spineIdRefs.mapNotNull { idRef ->
                    val item = manifest[idRef] ?: return@mapNotNull null
                    when {
                        item.mediaType in imageMediaTypes -> item.href
                        item.mediaType in xhtmlMediaTypes -> {
                            // Return the XHTML href — the caller will extract embedded images
                            item.href
                        }
                        else -> null
                    }
                }
            } else {
                // No spine: fall back to image manifest items in declaration order
                manifest.values
                    .filter { it.mediaType in imageMediaTypes }
                    .map { it.href }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private data class ManifestItem(val href: String, val mediaType: String)

    // ─── URL scheme helpers ───────────────────────────────────────────

    /**
     * Encodes a chapter as a URL-style string used as [SourceChapter.url].
     * Format: `<mangaPath>|chapter|<chapterName>` or `<mangaPath>` when chapterName is null.
     */
    private fun chapterUrl(mangaPath: String, chapterName: String?): String =
        if (chapterName == null) mangaPath else "$mangaPath${CHAPTER_SEPARATOR}$chapterName"

    private fun parseChapterUrl(url: String): Pair<String, String?> {
        val idx = url.lastIndexOf(CHAPTER_SEPARATOR)
        return if (idx == -1) url to null
        else url.substring(0, idx) to url.substring(idx + CHAPTER_SEPARATOR.length)
    }

    private fun findEntryByUrl(url: String): LocalMangaEntry? {
        val mangaPath = parseChapterUrl(url).first
        val file = File(mangaPath)

        if (!isWithinBaseDirectory(file)) return null

        val target = try { file.canonicalFile } catch (e: Exception) { return null }
        return when {
            target.isDirectory -> LocalMangaEntry.Folder(target)
            target.isFile -> LocalMangaEntry.Archive(target)
            else -> null
        }
    }

    // ─── Internal models ──────────────────────────────────────────────

    private sealed class LocalMangaEntry {
        abstract val name: String

        data class Folder(val file: File) : LocalMangaEntry() {
            override val name: String get() = file.name
        }

        data class Archive(val file: File) : LocalMangaEntry() {
            override val name: String get() = file.nameWithoutExtension
        }
    }

    private data class LocalMeta(
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null
    )

    companion object {
        private const val CHAPTER_SEPARATOR = "|chapter|"

        /**
         * Natural-order comparator for [String] values (ZIP entry names, filenames, etc.).
         * Handles numeric segments so "page_2.jpg" sorts before "page_10.jpg".
         */
        val NATURAL_STRING_ORDER: Comparator<String> = Comparator { a, b -> naturalCompare(a, b) }

        /**
         * Natural-order comparator for [File] objects sorted by filename.
         * Handles numeric segments so that "page_2.jpg" sorts before "page_10.jpg".
         */
        private val NATURAL_FILENAME_ORDER: Comparator<File> = Comparator { a, b ->
            naturalCompare(a.name, b.name)
        }

        /**
         * Compares two strings in natural order (numeric chunks compared numerically,
         * non-numeric chunks compared lexicographically, case-insensitively).
         */
        private fun naturalCompare(a: String, b: String): Int {
            var i = 0; var j = 0
            while (i < a.length && j < b.length) {
                val ca = a[i]; val cb = b[j]
                if (ca.isDigit() && cb.isDigit()) {
                    var numA = 0L; var numB = 0L
                    while (i < a.length && a[i].isDigit()) { numA = numA * 10 + (a[i++] - '0') }
                    while (j < b.length && b[j].isDigit()) { numB = numB * 10 + (b[j++] - '0') }
                    if (numA != numB) return numA.compareTo(numB)
                } else {
                    val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                    if (cmp != 0) return cmp
                    i++; j++
                }
            }
            return a.length - b.length
        }
    }
}
