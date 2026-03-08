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
    // getPageFile()
    // -------------------------------------------------------------------------

    @Test
    fun getPageFile_fileNameMatchesIndexPattern() {
        val root = tempDir()
        try {
            val pageFile = DownloadProvider.getPageFile(root, "MangaDex", "One_Piece", "Chapter_1", 3)
            assertEquals("3.jpg", pageFile.name)
            assertEquals("jpg", pageFile.extension)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun getPageFile_pathContainsRootDirAndSegments() {
        val root = tempDir()
        try {
            val pageFile = DownloadProvider.getPageFile(root, "Source", "Manga Title", "Ch 1", 0)
            val path = pageFile.absolutePath
            assertTrue(path.contains("OtakuReader"))
            assertTrue(path.contains("Source"))
            // sanitize() does not replace spaces, so the title is unchanged
            assertTrue(path.contains("Manga Title"))
            assertTrue(path.contains("Ch 1"))
            assertTrue(path.endsWith("0.jpg"))
        } finally {
            root.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // isChapterDownloaded()
    // -------------------------------------------------------------------------

    @Test
    fun isChapterDownloaded_nonExistentDir_returnsFalse() {
        val root = tempDir()
        try {
            assertFalse(DownloadProvider.isChapterDownloaded(root, "source", "manga", "ch1"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun isChapterDownloaded_emptyDir_returnsFalse() {
        val root = tempDir()
        try {
            DownloadProvider.getChapterDir(root, "source", "manga", "ch1").mkdirs()
            assertFalse(DownloadProvider.isChapterDownloaded(root, "source", "manga", "ch1"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun isChapterDownloaded_dirWithPageFile_returnsTrue() {
        val root = tempDir()
        try {
            val pageFile = DownloadProvider.getPageFile(root, "source", "manga", "ch1", 0)
            pageFile.parentFile?.mkdirs()
            pageFile.writeText("fake image data")
            assertTrue(DownloadProvider.isChapterDownloaded(root, "source", "manga", "ch1"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun isChapterDownloaded_nonImageFileOnly_returnsFalse() {
        val root = tempDir()
        try {
            val chapterDir = DownloadProvider.getChapterDir(root, "source", "manga", "ch1")
            chapterDir.mkdirs()
            File(chapterDir, "meta.json").writeText("{}")
            assertFalse(DownloadProvider.isChapterDownloaded(root, "source", "manga", "ch1"))
        } finally {
            root.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // getDownloadedPageUris()
    // -------------------------------------------------------------------------

    @Test
    fun getDownloadedPageUris_sortedByIndex() {
        val root = tempDir()
        try {
            val chapterDir = DownloadProvider.getChapterDir(root, "source", "manga", "ch1")
            chapterDir.mkdirs()
            listOf(2, 0, 5, 1).forEach { i -> File(chapterDir, "$i.jpg").writeText("data $i") }

            val uris = DownloadProvider.getDownloadedPageUris(root, "source", "manga", "ch1")
            assertEquals(4, uris.size)
            assertTrue(uris[0].endsWith("0.jpg"))
            assertTrue(uris[1].endsWith("1.jpg"))
            assertTrue(uris[2].endsWith("2.jpg"))
            assertTrue(uris[3].endsWith("5.jpg"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun getDownloadedPageUris_nonImageFilesIgnored() {
        val root = tempDir()
        try {
            val chapterDir = DownloadProvider.getChapterDir(root, "source", "manga", "ch1")
            chapterDir.mkdirs()
            File(chapterDir, "0.jpg").writeText("image")
            File(chapterDir, "meta.json").writeText("{}")
            File(chapterDir, "1.png").writeText("image2")

            val uris = DownloadProvider.getDownloadedPageUris(root, "source", "manga", "ch1")
            assertEquals(2, uris.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun getDownloadedPageUris_emptyDir_returnsEmptyList() {
        val root = tempDir()
        try {
            val uris = DownloadProvider.getDownloadedPageUris(root, "source", "manga", "ch1")
            assertTrue(uris.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
