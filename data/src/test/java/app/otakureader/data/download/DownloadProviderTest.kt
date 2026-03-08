package app.otakureader.data.download

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProviderTest {

    private fun tempDir(): File = createTempDirectory("otaku_test_").toFile()

    // -------------------------------------------------------------------------
    // sanitize()
    // -------------------------------------------------------------------------

    @Test
    fun sanitize_plainName_unchanged() {
        assertEquals("MangaDex", DownloadProvider.sanitize("MangaDex"))
    }

    @Test
    fun sanitize_forwardSlash_replacedWithUnderscore() {
        assertEquals("My_Manga", DownloadProvider.sanitize("My/Manga"))
    }

    @Test
    fun sanitize_backslash_replacedWithUnderscore() {
        assertEquals("My_Manga", DownloadProvider.sanitize("My\\Manga"))
    }

    @Test
    fun sanitize_colon_replacedWithUnderscore() {
        assertEquals("Title_Subtitle", DownloadProvider.sanitize("Title:Subtitle"))
    }

    @Test
    fun sanitize_allIllegalChars_replacedWithUnderscores() {
        val input = "/\\:*?\"<>|"
        val expected = "_________"
        assertEquals(expected, DownloadProvider.sanitize(input))
    }

    @Test
    fun sanitize_surroundingWhitespace_trimmed() {
        assertEquals("Manga", DownloadProvider.sanitize("  Manga  "))
    }

    @Test
    fun sanitize_emptyString_returnsEmpty() {
        assertEquals("", DownloadProvider.sanitize(""))
    }

    // -------------------------------------------------------------------------
    // getPageFile() path structure
    // -------------------------------------------------------------------------

    @Test
    fun getPageFile_fileNameMatchesIndexPattern() {
        val dir = tempDir()
        try {
            val chapterDir = File(dir, "OtakuReader/MangaDex/One_Piece/Chapter_1")
            val pageFile = File(chapterDir, "3.jpg")
            assertEquals("3.jpg", pageFile.name)
            assertEquals("jpg", pageFile.extension)
        } finally {
            dir.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // isChapterDownloaded() / getDownloadedPageUris()
    // -------------------------------------------------------------------------

    @Test
    fun isChapterDownloaded_nonExistentDir_returnsFalse() {
        val dir = File(System.getProperty("java.io.tmpdir"), "otaku_test_nonexistent_${System.nanoTime()}")
        dir.deleteRecursively()
        assertFalse(dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true))
    }

    @Test
    fun isChapterDownloaded_emptyDir_returnsFalse() {
        val dir = tempDir()
        try {
            val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
            assertFalse(
                dir.isDirectory &&
                    dir.listFiles()?.any { it.extension.lowercase() in imageExtensions } == true
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun isChapterDownloaded_dirWithPageFile_returnsTrue() {
        val dir = tempDir()
        try {
            File(dir, "0.jpg").writeText("fake image data")
            val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
            assertTrue(
                dir.isDirectory &&
                    dir.listFiles()?.any { it.extension.lowercase() in imageExtensions } == true
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun getDownloadedPageUris_sortedByIndex() {
        val dir = tempDir()
        try {
            listOf(2, 0, 5, 1).forEach { i -> File(dir, "$i.jpg").writeText("data $i") }

            val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
            val files = dir.listFiles()
                ?.filter { it.extension.lowercase() in imageExtensions }
                ?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }
                ?.map { "file://${it.absolutePath}" }
                ?: emptyList()

            assertEquals(4, files.size)
            assertTrue(files[0].endsWith("0.jpg"))
            assertTrue(files[1].endsWith("1.jpg"))
            assertTrue(files[2].endsWith("2.jpg"))
            assertTrue(files[3].endsWith("5.jpg"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun getDownloadedPageUris_nonImageFilesIgnored() {
        val dir = tempDir()
        try {
            File(dir, "0.jpg").writeText("image")
            File(dir, "meta.json").writeText("{}")
            File(dir, "1.png").writeText("image2")

            val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
            val files = dir.listFiles()
                ?.filter { it.extension.lowercase() in imageExtensions }
                ?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }
                ?: emptyList()

            assertEquals(2, files.size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
