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

    // -------------------------------------------------------------------------
    // CBZ support – isChapterDownloaded()
    // -------------------------------------------------------------------------

    @Test
    fun isChapterDownloaded_cbzFileOnly_returnsTrue() {
        val root = tempDir()
        try {
            val chapterDir = DownloadProvider.getChapterDir(root, "source", "manga", "ch1")
            chapterDir.mkdirs()
            File(chapterDir, CbzCreator.CBZ_FILE_NAME).writeText("fake cbz")
            assertTrue(DownloadProvider.isChapterDownloaded(root, "source", "manga", "ch1"))
        } finally {
            root.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // CBZ support – getCbzFile()
    // -------------------------------------------------------------------------

    @Test
    fun getCbzFile_returnsCorrectPath() {
        val root = tempDir()
        try {
            val cbzFile = DownloadProvider.getCbzFile(root, "source", "manga", "ch1")
            assertEquals(CbzCreator.CBZ_FILE_NAME, cbzFile.name)
            assertTrue(cbzFile.absolutePath.contains("OtakuReader"))
            assertTrue(cbzFile.absolutePath.contains("source"))
            assertTrue(cbzFile.absolutePath.contains("manga"))
            assertTrue(cbzFile.absolutePath.contains("ch1"))
        } finally {
            root.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // CBZ support – getDownloadedPageUris() extracts from CBZ
    // -------------------------------------------------------------------------

    @Test
    fun getDownloadedPageUris_cbzOnly_extractsAndReturnsUris() {
        val root = tempDir()
        try {
            val chapterDir = DownloadProvider.getChapterDir(root, "source", "manga", "ch1")
            chapterDir.mkdirs()
            // Create two page files and pack into CBZ
            File(chapterDir, "0.jpg").writeText("page0")
            File(chapterDir, "1.jpg").writeText("page1")
            CbzCreator.createCbz(chapterDir)
            // Remove the loose files to simulate CBZ-only storage
            File(chapterDir, "0.jpg").delete()
            File(chapterDir, "1.jpg").delete()

            val uris = DownloadProvider.getDownloadedPageUris(root, "source", "manga", "ch1")
            assertEquals(2, uris.size)
            // Pages are extracted into a .pages subdirectory; URIs still end with page filenames.
            assertTrue(uris[0].endsWith("0.jpg"))
            assertTrue(uris[1].endsWith("1.jpg"))
            // Verify pages live in the cache subdir, not at the chapter dir level
            assertTrue(uris[0].contains("/.pages/"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun getDownloadedPageUris_cbzOnly_usesExistingCacheOnSecondCall() {
        val root = tempDir()
        try {
            val chapterDir = DownloadProvider.getChapterDir(root, "source", "manga", "ch1")
            chapterDir.mkdirs()
            File(chapterDir, "0.jpg").writeText("page0")
            CbzCreator.createCbz(chapterDir)
            File(chapterDir, "0.jpg").delete()

            // First call - extracts from CBZ
            val uris1 = DownloadProvider.getDownloadedPageUris(root, "source", "manga", "ch1")
            assertEquals(1, uris1.size)

            // Second call - should reuse the cached extraction
            val uris2 = DownloadProvider.getDownloadedPageUris(root, "source", "manga", "ch1")
            assertEquals(1, uris2.size)
            assertEquals(uris1[0], uris2[0])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun getDownloadedPageUris_prefersLooseFilesOverCbz() {
        val root = tempDir()
        try {
            val chapterDir = DownloadProvider.getChapterDir(root, "source", "manga", "ch1")
            chapterDir.mkdirs()
            File(chapterDir, "0.jpg").writeText("loose page")
            File(chapterDir, CbzCreator.CBZ_FILE_NAME).writeText("fake cbz")

            val uris = DownloadProvider.getDownloadedPageUris(root, "source", "manga", "ch1")
            assertEquals(1, uris.size)
            assertTrue(uris[0].endsWith("0.jpg"))
            // Loose files are at chapter dir level, not inside .pages
            assertFalse(uris[0].contains("/.pages/"))
        } finally {
            root.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // migrateChapterDownload()
    // -------------------------------------------------------------------------

    @Test
    fun migrateChapterDownload_moveMode_movesFiles() {
        val root = tempDir()
        try {
            // Create source chapter with loose pages
            val sourceDir = DownloadProvider.getChapterDir(root, "source1", "manga1", "ch1")
            sourceDir.mkdirs()
            File(sourceDir, "0.jpg").writeText("page0")
            File(sourceDir, "1.jpg").writeText("page1")

            // Migrate in move mode (copy=false)
            val result = DownloadProvider.migrateChapterDownload(
                root, "source1", "manga1", "ch1",
                "source2", "manga2", "ch2",
                copy = false
            )

            assertTrue(result)

            // Source directory should be deleted
            assertFalse(sourceDir.exists())

            // Target directory should have the files
            val targetDir = DownloadProvider.getChapterDir(root, "source2", "manga2", "ch2")
            assertTrue(targetDir.exists())
            assertTrue(File(targetDir, "0.jpg").exists())
            assertTrue(File(targetDir, "1.jpg").exists())
            assertEquals("page0", File(targetDir, "0.jpg").readText())
            assertEquals("page1", File(targetDir, "1.jpg").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migrateChapterDownload_copyMode_copiesFiles() {
        val root = tempDir()
        try {
            // Create source chapter with loose pages
            val sourceDir = DownloadProvider.getChapterDir(root, "source1", "manga1", "ch1")
            sourceDir.mkdirs()
            File(sourceDir, "0.jpg").writeText("page0")
            File(sourceDir, "1.jpg").writeText("page1")

            // Migrate in copy mode (copy=true)
            val result = DownloadProvider.migrateChapterDownload(
                root, "source1", "manga1", "ch1",
                "source2", "manga2", "ch2",
                copy = true
            )

            assertTrue(result)

            // Source directory should still exist
            assertTrue(sourceDir.exists())
            assertTrue(File(sourceDir, "0.jpg").exists())
            assertTrue(File(sourceDir, "1.jpg").exists())

            // Target directory should also have the files
            val targetDir = DownloadProvider.getChapterDir(root, "source2", "manga2", "ch2")
            assertTrue(targetDir.exists())
            assertTrue(File(targetDir, "0.jpg").exists())
            assertTrue(File(targetDir, "1.jpg").exists())
            assertEquals("page0", File(targetDir, "0.jpg").readText())
            assertEquals("page1", File(targetDir, "1.jpg").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migrateChapterDownload_withCbzFile_migratesCbz() {
        val root = tempDir()
        try {
            // Create source chapter with CBZ
            val sourceDir = DownloadProvider.getChapterDir(root, "source1", "manga1", "ch1")
            sourceDir.mkdirs()
            File(sourceDir, "0.jpg").writeText("page0")
            CbzCreator.createCbz(sourceDir)
            File(sourceDir, "0.jpg").delete() // Remove loose file, keep only CBZ

            // Migrate
            val result = DownloadProvider.migrateChapterDownload(
                root, "source1", "manga1", "ch1",
                "source2", "manga2", "ch2",
                copy = false
            )

            assertTrue(result)

            // Target should have CBZ
            val targetCbz = DownloadProvider.getCbzFile(root, "source2", "manga2", "ch2")
            assertTrue(targetCbz.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migrateChapterDownload_withPagesCacheSubdir_migratesSubdir() {
        val root = tempDir()
        try {
            // Create source chapter with .pages cache subdirectory
            val sourceDir = DownloadProvider.getChapterDir(root, "source1", "manga1", "ch1")
            sourceDir.mkdirs()
            File(sourceDir, CbzCreator.CBZ_FILE_NAME).writeText("fake cbz")
            val pagesCache = File(sourceDir, ".pages")
            pagesCache.mkdirs()
            File(pagesCache, "0.jpg").writeText("cached page0")
            File(pagesCache, "1.jpg").writeText("cached page1")

            // Migrate
            val result = DownloadProvider.migrateChapterDownload(
                root, "source1", "manga1", "ch1",
                "source2", "manga2", "ch2",
                copy = false
            )

            assertTrue(result)

            // Target should have .pages subdirectory
            val targetPagesCache = File(
                DownloadProvider.getChapterDir(root, "source2", "manga2", "ch2"),
                ".pages"
            )
            assertTrue(targetPagesCache.exists())
            assertTrue(File(targetPagesCache, "0.jpg").exists())
            assertTrue(File(targetPagesCache, "1.jpg").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migrateChapterDownload_nonExistentSource_returnsFalse() {
        val root = tempDir()
        try {
            val result = DownloadProvider.migrateChapterDownload(
                root, "source1", "manga1", "ch1",
                "source2", "manga2", "ch2",
                copy = false
            )

            assertFalse(result)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migrateChapterDownload_emptySourceDir_returnsFalse() {
        val root = tempDir()
        try {
            // Create empty source directory
            val sourceDir = DownloadProvider.getChapterDir(root, "source1", "manga1", "ch1")
            sourceDir.mkdirs()

            val result = DownloadProvider.migrateChapterDownload(
                root, "source1", "manga1", "ch1",
                "source2", "manga2", "ch2",
                copy = false
            )

            assertFalse(result)
        } finally {
            root.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // migrateChapterDownload() - Copy verification tests
    // -------------------------------------------------------------------------

    @Test
    fun migrateChapterDownload_verifyFileSize_afterCopyFallback() {
        val root = tempDir()
        try {
            // Create source chapter with files
            val sourceDir = DownloadProvider.getChapterDir(root, "source1", "manga1", "ch1")
            sourceDir.mkdirs()
            val sourceFile = File(sourceDir, "0.jpg")
            // Create file with specific content
            val content = "This is test image data with specific size"
            sourceFile.writeText(content)

            // Simulate cross-filesystem scenario: create destination on different path
            // that would cause renameTo to fail (we can't truly simulate this in tests,
            // but the verification code would catch corrupted copies)
            val result = DownloadProvider.migrateChapterDownload(
                root, "source1", "manga1", "ch1",
                "source2", "manga2", "ch2",
                copy = false
            )

            assertTrue(result)

            // Verify file was migrated and has correct size
            val targetDir = DownloadProvider.getChapterDir(root, "source2", "manga2", "ch2")
            val targetFile = File(targetDir, "0.jpg")
            assertTrue(targetFile.exists())
            assertEquals(content.length.toLong(), targetFile.length())
            assertEquals(content, targetFile.readText())

            // Source should be deleted
            assertFalse(sourceFile.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migrateChapterDownload_verifyDirectoryContents_afterRecursiveCopy() {
        val root = tempDir()
        try {
            // Create source chapter with subdirectory containing multiple files
            val sourceDir = DownloadProvider.getChapterDir(root, "source1", "manga1", "ch1")
            sourceDir.mkdirs()
            File(sourceDir, CbzCreator.CBZ_FILE_NAME).writeText("fake cbz")

            val pagesCache = File(sourceDir, ".pages")
            pagesCache.mkdirs()
            File(pagesCache, "0.jpg").writeText("page 0 content")
            File(pagesCache, "1.jpg").writeText("page 1 content")
            File(pagesCache, "2.jpg").writeText("page 2 content")

            val result = DownloadProvider.migrateChapterDownload(
                root, "source1", "manga1", "ch1",
                "source2", "manga2", "ch2",
                copy = false
            )

            assertTrue(result)

            // Verify all files were migrated
            val targetPagesCache = File(
                DownloadProvider.getChapterDir(root, "source2", "manga2", "ch2"),
                ".pages"
            )
            assertTrue(targetPagesCache.isDirectory)
            val targetFiles = targetPagesCache.listFiles()?.sortedBy { it.name } ?: emptyList()
            assertEquals(3, targetFiles.size)
            assertEquals("page 0 content", File(targetPagesCache, "0.jpg").readText())
            assertEquals("page 1 content", File(targetPagesCache, "1.jpg").readText())
            assertEquals("page 2 content", File(targetPagesCache, "2.jpg").readText())

            // Source should be deleted
            assertFalse(pagesCache.exists())
            assertFalse(sourceDir.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
