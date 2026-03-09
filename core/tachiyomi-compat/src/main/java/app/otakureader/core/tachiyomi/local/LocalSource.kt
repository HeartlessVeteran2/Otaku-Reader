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
 * Metadata sources (in priority order):
 * 1. `ComicInfo.xml` inside a CBZ/ZIP archive
 * 2. `series.json` in the manga's root folder
 * 3. Name inferred from the file/directory name
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
     * Resolves a chapter URL (produced by [chapterUrl]) into a list of [Page] objects
     * pointing to local file URIs so the image loader can display them without network.
     */
    private suspend fun resolvePages(chapterUrl: String): List<Page> = withContext(Dispatchers.IO) {
        val (mangaPath, chapterName) = parseChapterUrl(chapterUrl)
        val mangaFile = File(mangaPath)

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
            val cacheDir = File(context.cacheDir, "local_source/${archive.nameWithoutExtension}")
            cacheDir.mkdirs()

            ZipFile(archive).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in imageExtensions }
                    .sortedWith(compareBy { it.name })
                    .mapIndexed { index, entry ->
                        // Sanitize: flatten any directory structure to prevent path traversal
                        val safeName = entry.name.replace('/', '_').replace('\\', '_')
                            .trimStart('.')
                        val outFile = File(cacheDir, safeName)
                        // Guard against path traversal attacks
                        if (!outFile.canonicalPath.startsWith(cacheDir.canonicalPath)) {
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
            val cacheDir = File(context.cacheDir, "local_source/${epub.nameWithoutExtension}")
            cacheDir.mkdirs()

            ZipFile(epub).use { zip ->
                // Find the OPF to get the reading order
                val opfEntry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf") }

                val orderedImagePaths: List<String> = if (opfEntry != null) {
                    parseOpfSpineImagePaths(zip.getInputStream(opfEntry))
                } else {
                    emptyList()
                }

                // Collect all image entries
                val imageEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in imageExtensions }
                    .associateBy { it.name }

                // Use spine order if available, fall back to alphabetical
                val sortedEntries = if (orderedImagePaths.isNotEmpty()) {
                    orderedImagePaths.mapNotNull { path ->
                        imageEntries.entries.firstOrNull { (key, _) -> key.endsWith(path) }?.value
                    }.ifEmpty { imageEntries.values.sortedBy { it.name } }
                } else {
                    imageEntries.values.sortedBy { it.name }
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
     * Parse content.opf and return image paths referenced from the spine (in order).
     */
    private fun parseOpfSpineImagePaths(stream: InputStream): List<String> {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, null)

            // manifest: id → href
            val manifest = mutableMapOf<String, String>()
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
                                if (id != null && href != null && mediaType.startsWith("image/")) {
                                    manifest[id] = href
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

            if (spineIdRefs.isNotEmpty()) {
                spineIdRefs.mapNotNull { manifest[it] }
            } else {
                manifest.values.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

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

        // Ensure the resolved file is within the configured base directory to
        // prevent access to arbitrary filesystem paths via crafted URLs.
        val baseDir = try {
            File(directory).canonicalFile
        } catch (e: Exception) {
            return null
        }

        val target = try {
            file.canonicalFile
        } catch (e: Exception) {
            return null
        }

        val basePath = baseDir.path
        val targetPath = target.path

        // Allow the base directory itself and any descendants.
        val isUnderBaseDir = target == baseDir ||
            (targetPath.startsWith(basePath) &&
                (targetPath.length == basePath.length ||
                 targetPath[basePath.length] == File.separatorChar))

        if (!isUnderBaseDir) {
            return null
        }

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
