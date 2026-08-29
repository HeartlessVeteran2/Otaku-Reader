package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.PageBookmarkEntity
import app.otakureader.core.database.entity.ReaderCommentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for #1254 — re-inserting an existing chapter must not change its id.
 *
 * `ChapterDao` used `@Insert(onConflict = REPLACE)` against the unique `(mangaId, url)` index.
 * SQLite's REPLACE deletes the conflicting row and inserts a new one, and because `id` is
 * `autoGenerate` the replacement got a *different* id. Six tables store a chapter id; three of them
 * declare `ON DELETE CASCADE`, so a refresh could destroy reading history, page bookmarks and reader
 * comments outright, while the rest were left dangling.
 *
 * These assert **state left behind** rather than return values, per the repo's self-review
 * checklist: the id, and whether the dependent rows survived.
 */
@RunWith(AndroidJUnit4::class)
class ChapterUpsertTest {

    private lateinit var database: OtakuReaderDatabase
    private lateinit var chapterDao: ChapterDao
    private lateinit var mangaDao: MangaDao
    private lateinit var readingHistoryDao: ReadingHistoryDao
    private lateinit var pageBookmarkDao: PageBookmarkDao
    private lateinit var readerCommentDao: ReaderCommentDao

    private val mangaId = 1L
    private val chapterUrl = "/chapter-1"

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java
        ).allowMainThreadQueries().build()
        chapterDao = database.chapterDao()
        mangaDao = database.mangaDao()
        readingHistoryDao = database.readingHistoryDao()
        pageBookmarkDao = database.pageBookmarkDao()
        readerCommentDao = database.readerCommentDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun insertManga() {
        mangaDao.insertOrGetExisting(
            MangaEntity(id = mangaId, title = "Test Manga", sourceId = 1L, url = "/manga", favorite = true)
        )
    }

    private fun sourceChapter(name: String, sourceOrder: Int = 1) = ChapterEntity(
        mangaId = mangaId,
        url = chapterUrl,
        name = name,
        sourceOrder = sourceOrder,
    )

    @Test
    fun `re-upserting the same chapter keeps its id`() = runBlocking {
        insertManga()
        val firstId = chapterDao.upsert(sourceChapter(name = "Chapter 1"))

        val secondId = chapterDao.upsert(sourceChapter(name = "Chapter 1"))

        assertEquals(firstId, secondId)
        // And the row itself, not just the returned value — a REPLACE would have left a different
        // id behind while still returning something plausible.
        assertEquals(firstId, chapterDao.getChapterByMangaIdAndUrl(mangaId, chapterUrl)?.id)
        assertEquals(1, chapterDao.getChaptersByMangaIdOnce(mangaId).size)
    }

    /**
     * The case that made this bug destructive rather than merely untidy.
     *
     * Whether SQLite fires ON DELETE CASCADE for the implicit delete REPLACE performs is not stated
     * on either authoritative SQLite page, so it is settled here by experiment rather than
     * inference. With the upsert in place no delete happens at all, so the question is moot — which
     * is the point of the fix.
     */
    @Test
    fun `re-upserting a chapter preserves its history, bookmarks and comments`() = runBlocking {
        insertManga()
        val chapterId = chapterDao.upsert(sourceChapter(name = "Chapter 1"))

        readingHistoryDao.upsert(chapterId = chapterId, readAt = 1_000L, readDurationMs = 5_000L)
        pageBookmarkDao.insertBookmark(
            PageBookmarkEntity(mangaId = mangaId, chapterId = chapterId, pageIndex = 3)
        )
        readerCommentDao.insert(
            ReaderCommentEntity(mangaId = mangaId, chapterId = chapterId, body = "note")
        )

        // A library refresh re-inserting the same chapter, exactly as UpdateLibraryMangaUseCase does
        // when two updates overlap.
        chapterDao.upsert(sourceChapter(name = "Chapter 1"))

        assertTrue(
            "reading history was destroyed by the re-insert",
            readingHistoryDao.observeHistory().first().any { it.chapterId == chapterId }
        )
        assertTrue(
            "page bookmark was destroyed by the re-insert",
            pageBookmarkDao.getBookmarksForChapter(chapterId).first().isNotEmpty()
        )
        assertTrue(
            "reader comment was destroyed by the re-insert",
            readerCommentDao.getChapterComments(chapterId).first().isNotEmpty()
        )
    }

    /**
     * A source returning the same chapter URL twice in one response is real, and it lands in a
     * single [ChapterDao.upsertAll] batch. Update-then-insert handles it: the second entry finds the
     * row the first inserted and refreshes it.
     */
    @Test
    fun `upsertAll with the same url twice stores one row`() = runBlocking {
        insertManga()

        chapterDao.upsertAll(
            listOf(
                sourceChapter(name = "Chapter 1"),
                sourceChapter(name = "Chapter 1 (dup)"),
            )
        )

        val stored = chapterDao.getChaptersByMangaIdOnce(mangaId)
        assertEquals(1, stored.size)
        assertEquals("Chapter 1 (dup)", stored.single().name)
    }

    @Test
    fun `re-upserting preserves read progress and user notes`() = runBlocking {
        insertManga()
        val chapterId = chapterDao.upsert(sourceChapter(name = "Chapter 1"))
        chapterDao.updateChapterProgress(chapterId = chapterId, read = true, lastPageRead = 12)
        chapterDao.updateChapterNotes(chapterId = chapterId, notes = "my note")

        chapterDao.upsert(sourceChapter(name = "Chapter 1"))

        val stored = chapterDao.getChapterById(chapterId)
        assertNotNull(stored)
        assertTrue("read flag was reset by a metadata refresh", stored!!.read)
        assertEquals("lastPageRead was reset by a metadata refresh", 12, stored.lastPageRead)
        assertEquals("userNotes were lost by a metadata refresh", "my note", stored.userNotes)
    }

    /**
     * The other half of the contract: a genuine metadata refresh must still apply, or a renamed
     * chapter would be frozen at whatever it was first seen as.
     */
    @Test
    fun `re-upserting applies refreshed source metadata`() = runBlocking {
        insertManga()
        val chapterId = chapterDao.upsert(sourceChapter(name = "Chapter 1"))

        chapterDao.upsert(
            sourceChapter(name = "Chapter 1 (revised)").copy(dateUpload = 500L, scanlator = "Group")
        )

        val stored = chapterDao.getChapterById(chapterId)
        assertEquals("Chapter 1 (revised)", stored?.name)
        assertEquals(500L, stored?.dateUpload)
        assertEquals("Group", stored?.scanlator)
    }

    /**
     * `Chapter.toEntity()` cannot populate `sourceOrder` — `domain.model.Chapter` has no such field,
     * so every entity arriving from a library refresh carries `0`. Writing that through would zero
     * the ordering `TachiyomiBackupImporter` imported, and four queries here `ORDER BY sourceOrder`.
     */
    @Test
    fun `re-upserting preserves sourceOrder against an incoming zero`() = runBlocking {
        insertManga()
        val chapterId = chapterDao.upsert(sourceChapter(name = "Chapter 1", sourceOrder = 19))

        chapterDao.upsert(sourceChapter(name = "Chapter 1", sourceOrder = 0))

        assertEquals(19, chapterDao.getChapterById(chapterId)?.sourceOrder)
    }

    /**
     * The unknown-value sentinels this codebase already uses. An incoming default means "the fetch
     * did not tell us", not "the value became unknown", so it must not overwrite what is stored.
     */
    @Test
    fun `re-upserting does not overwrite stored values with unknown sentinels`() = runBlocking {
        insertManga()
        val chapterId = chapterDao.upsert(
            sourceChapter(name = "Chapter 1").copy(
                scanlator = "Group",
                chapterNumber = 12f,
                dateUpload = 900L,
            )
        )

        // Defaults: scanlator null, chapterNumber -1f, dateUpload 0.
        chapterDao.upsert(sourceChapter(name = "Chapter 1"))

        val stored = chapterDao.getChapterById(chapterId)
        assertEquals("a null scanlator wiped a known one", "Group", stored?.scanlator)
        assertEquals(
            "the -1f sentinel overwrote a known chapterNumber",
            12f,
            stored?.chapterNumber ?: 0f,
            0.001f,
        )
        assertEquals("a zero dateUpload overwrote a known one", 900L, stored?.dateUpload)
    }
}
