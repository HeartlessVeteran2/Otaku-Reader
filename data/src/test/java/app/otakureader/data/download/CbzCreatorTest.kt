package app.otakureader.data.download

import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CbzCreatorTest {

    private fun tempDir(): File = createTempDirectory("cbz_test_").toFile()

    // -------------------------------------------------------------------------
    // createCbz()
    // -------------------------------------------------------------------------

    @Test
    fun createCbz_createsArchiveWithAllPageFiles() {
        val root = tempDir()
        try {
            listOf("0.jpg", "1.jpg", "2.png").forEach { name ->
                File(root, name).writeText("image data $name")
            }
            val result = CbzCreator.createCbz(root)
            assertTrue(result.isSuccess)
            val cbz = result.getOrThrow()
            assertTrue(cbz.exists())
            assertEquals(CbzCreator.CBZ_FILE_NAME, cbz.name)

            ZipFile(cbz).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toSet()
                assertTrue(entries.contains("0.jpg"))
                assertTrue(entries.contains("1.jpg"))
                assertTrue(entries.contains("2.png"))
                assertFalse(entries.contains("ComicInfo.xml"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun createCbz_withMetadata_embedsComicInfoXml() {
        val root = tempDir()
        try {
            File(root, "0.jpg").writeText("image data")
            val metadata = CbzCreator.ComicInfoMetadata(
                title = "Chapter 1",
                series = "My Manga",
                number = "1",
                writer = "Author",
                language = "en"
            )
            val result = CbzCreator.createCbz(root, metadata)
            assertTrue(result.isSuccess)

            ZipFile(result.getOrThrow()).use { zip ->
                val comicInfoEntry = zip.getEntry("ComicInfo.xml")
                assertTrue(comicInfoEntry != null)
                val xml = zip.getInputStream(comicInfoEntry).use { it.reader().readText() }
                assertTrue(xml.contains("<Title>Chapter 1</Title>"))
                assertTrue(xml.contains("<Series>My Manga</Series>"))
                assertTrue(xml.contains("<Number>1</Number>"))
                assertTrue(xml.contains("<Writer>Author</Writer>"))
                assertTrue(xml.contains("<LanguageISO>en</LanguageISO>"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun createCbz_emptyDirectory_returnsFailure() {
        val root = tempDir()
        try {
            val result = CbzCreator.createCbz(root)
            assertTrue("Expected failure for empty directory", result.isFailure)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun createCbz_existingCbz_returnsExistingWithoutOverwrite() {
        val root = tempDir()
        try {
            File(root, "0.jpg").writeText("image data")
            // First creation
            val first = CbzCreator.createCbz(root)
            assertTrue(first.isSuccess)
            val firstCbz = first.getOrThrow()
            val originalLastModified = firstCbz.lastModified()

            // Add another page and attempt re-creation – should return existing file unchanged.
            File(root, "1.jpg").writeText("another image")
            val second = CbzCreator.createCbz(root)
            assertTrue(second.isSuccess)
            val secondCbz = second.getOrThrow()

            assertEquals(firstCbz.absolutePath, secondCbz.absolutePath)
            assertEquals(originalLastModified, secondCbz.lastModified())
            // Only 0.jpg should be in the archive (1.jpg was added after creation)
            ZipFile(secondCbz).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toSet()
                assertTrue(entries.contains("0.jpg"))
                assertFalse(entries.contains("1.jpg"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun createCbz_nonDirectoryInput_returnsFailure() {
        val root = tempDir()
        try {
            val notADir = File(root, "file.txt").also { it.writeText("data") }
            val result = CbzCreator.createCbz(notADir)
            assertTrue(result.isFailure)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun createCbz_skipsNonImageFiles() {
        val root = tempDir()
        try {
            File(root, "0.jpg").writeText("image")
            File(root, "meta.json").writeText("{}")
            File(root, "readme.txt").writeText("read me")
            val result = CbzCreator.createCbz(root)
            assertTrue(result.isSuccess)
            ZipFile(result.getOrThrow()).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toSet()
                assertTrue(entries.contains("0.jpg"))
                assertFalse(entries.contains("meta.json"))
                assertFalse(entries.contains("readme.txt"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // extractCbzPages()
    // -------------------------------------------------------------------------

    @Test
    fun extractCbzPages_extractsAllPageFiles() {
        val root = tempDir()
        try {
            val pagesDir = File(root, "pages").also { it.mkdirs() }
            listOf("0.jpg", "1.png", "2.jpg").forEach { name ->
                File(pagesDir, name).writeBytes(name.toByteArray())
            }
            CbzCreator.createCbz(pagesDir)

            val destDir = File(root, "extracted").also { it.mkdirs() }
            val cbzFile = File(pagesDir, CbzCreator.CBZ_FILE_NAME)
            val result = CbzCreator.extractCbzPages(cbzFile, destDir)
            assertTrue(result.isSuccess)

            val extractedNames = result.getOrThrow().map { it.name }.toSet()
            assertTrue(extractedNames.contains("0.jpg"))
            assertTrue(extractedNames.contains("1.png"))
            assertTrue(extractedNames.contains("2.jpg"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun extractCbzPages_returnsFilesSortedByIndex() {
        val root = tempDir()
        try {
            val pagesDir = File(root, "pages").also { it.mkdirs() }
            listOf(2, 0, 4, 1).forEach { i -> File(pagesDir, "$i.jpg").writeText("data $i") }
            CbzCreator.createCbz(pagesDir)

            val destDir = File(root, "out").also { it.mkdirs() }
            val result = CbzCreator.extractCbzPages(File(pagesDir, CbzCreator.CBZ_FILE_NAME), destDir)
            assertTrue(result.isSuccess)
            val names = result.getOrThrow().map { it.name }
            assertEquals(listOf("0.jpg", "1.jpg", "2.jpg", "4.jpg"), names)
        } finally {
            root.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // buildComicInfoXml()
    // -------------------------------------------------------------------------

    @Test
    fun buildComicInfoXml_escapesSpecialCharsInTitle() {
        val metadata = CbzCreator.ComicInfoMetadata(
            title = "A & B < C > D",
            series = "Series"
        )
        val xml = CbzCreator.buildComicInfoXml(metadata)
        assertTrue(xml.contains("A &amp; B &lt; C &gt; D"))
    }

    @Test
    fun buildComicInfoXml_omitsOptionalFieldsWhenNull() {
        val metadata = CbzCreator.ComicInfoMetadata(title = "Ch 1", series = "S")
        val xml = CbzCreator.buildComicInfoXml(metadata)
        assertFalse(xml.contains("<Number>"))
        assertFalse(xml.contains("<Writer>"))
        assertFalse(xml.contains("<LanguageISO>"))
    }
}
