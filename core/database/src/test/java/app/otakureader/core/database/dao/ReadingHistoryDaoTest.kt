package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingHistoryDaoTest {

    private lateinit var database: OtakuReaderDatabase
    private lateinit var readingHistoryDao: ReadingHistoryDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var mangaDao: MangaDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java
        ).allowMainThreadQueries().build()
        readingHistoryDao = database.readingHistoryDao()
        chapterDao = database.chapterDao()
        mangaDao = database.mangaDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun upsert_accumulatesDurationOnConflict() = runBlocking {
        // Setup: Insert test manga and chapter
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(
                id = 1L,
                title = "Test Manga",
                sourceId = 1L,
                url = "url",
                favorite = true
            )
        )
        val chapterId = chapterDao.upsert(
            ChapterEntity(
                id = 1L,
                mangaId = mangaId,
                url = "chapter_url",
                name = "Chapter 1",
                read = false,
                chapterNumber = 1f
            )
        )

        // First upsert: Insert new history entry with 1000ms read time
        readingHistoryDao.upsert(chapterId, readAt = 100L, readDurationMs = 1000L)

        // Verify initial insert
        val history1 = readingHistoryDao.observeHistory().first()
        assertEquals(1, history1.size)
        assertEquals(chapterId, history1[0].chapterId)
        assertEquals(100L, history1[0].readAt)
        assertEquals(1000L, history1[0].readDurationMs)

        // Second upsert: Update with newer timestamp and additional duration
        readingHistoryDao.upsert(chapterId, readAt = 200L, readDurationMs = 500L)

        // Verify duration accumulation and timestamp update
        val history2 = readingHistoryDao.observeHistory().first()
        assertEquals(1, history2.size)
        assertEquals(chapterId, history2[0].chapterId)
        assertEquals(200L, history2[0].readAt) // Newer timestamp
        assertEquals(1500L, history2[0].readDurationMs) // Accumulated: 1000 + 500
    }

    @Test
    fun replaceHistory_overwritesDurationWithoutAccumulation() = runBlocking {
        // Setup: Insert test manga and chapter
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(
                id = 1L,
                title = "Test Manga",
                sourceId = 1L,
                url = "url",
                favorite = true
            )
        )
        val chapterId = chapterDao.upsert(
            ChapterEntity(
                id = 1L,
                mangaId = mangaId,
                url = "chapter_url",
                name = "Chapter 1",
                read = false,
                chapterNumber = 1f
            )
        )

        // First upsert: Insert new history entry with 1000ms read time
        readingHistoryDao.upsert(chapterId, readAt = 100L, readDurationMs = 1000L)

        // Verify initial insert
        val history1 = readingHistoryDao.observeHistory().first()
        assertEquals(1, history1.size)
        assertEquals(1000L, history1[0].readDurationMs)

        // Use replaceHistory: Should overwrite duration instead of accumulating
        readingHistoryDao.replaceHistory(chapterId, readAt = 200L, readDurationMs = 500L)

        // Verify duration replacement (NOT accumulation)
        val history2 = readingHistoryDao.observeHistory().first()
        assertEquals(1, history2.size)
        assertEquals(chapterId, history2[0].chapterId)
        assertEquals(200L, history2[0].readAt)
        assertEquals(500L, history2[0].readDurationMs) // Replaced, not accumulated
    }

    @Test
    fun replaceHistory_preservesRowIdAvoidingDeleteTriggers() = runBlocking {
        // Setup: Insert test manga and chapter
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(
                id = 1L,
                title = "Test Manga",
                sourceId = 1L,
                url = "url",
                favorite = true
            )
        )
        val chapterId = chapterDao.upsert(
            ChapterEntity(
                id = 1L,
                mangaId = mangaId,
                url = "chapter_url",
                name = "Chapter 1",
                read = false,
                chapterNumber = 1f
            )
        )

        // Insert initial history
        readingHistoryDao.upsert(chapterId, readAt = 100L, readDurationMs = 1000L)
        val history1 = readingHistoryDao.observeHistory().first()
        val originalId = history1[0].id

        // Replace history (should preserve row id)
        readingHistoryDao.replaceHistory(chapterId, readAt = 200L, readDurationMs = 500L)
        val history2 = readingHistoryDao.observeHistory().first()
        val newId = history2[0].id

        // Verify row id is preserved (UPDATE-then-INSERT pattern, not DELETE+INSERT)
        assertEquals(originalId, newId)
    }

    @Test
    fun replaceHistory_restoreSemantics_repeatedRestore() = runBlocking {
        // Simulate interrupted restore scenario: same backup restored multiple times
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(
                id = 1L,
                title = "Test Manga",
                sourceId = 1L,
                url = "url",
                favorite = true
            )
        )
        val chapterId = chapterDao.upsert(
            ChapterEntity(
                id = 1L,
                mangaId = mangaId,
                url = "chapter_url",
                name = "Chapter 1",
                read = false,
                chapterNumber = 1f
            )
        )

        // Backed-up values from restore file
        val backedUpReadAt = 1000L
        val backedUpDuration = 3000L

        // First restore
        readingHistoryDao.replaceHistory(chapterId, backedUpReadAt, backedUpDuration)
        val history1 = readingHistoryDao.observeHistory().first()
        assertEquals(backedUpDuration, history1[0].readDurationMs)

        // Second restore (simulating interrupted restore being re-run)
        readingHistoryDao.replaceHistory(chapterId, backedUpReadAt, backedUpDuration)
        val history2 = readingHistoryDao.observeHistory().first()

        // Verify duration is NOT accumulated on repeated restore
        assertEquals(backedUpDuration, history2[0].readDurationMs)
        assertEquals(backedUpReadAt, history2[0].readAt)
    }

    @Test
    fun upsert_insertsNewEntryWhenNoConflict() = runBlocking {
        // Setup: Insert test manga and chapter
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(
                id = 1L,
                title = "Test Manga",
                sourceId = 1L,
                url = "url",
                favorite = true
            )
        )
        val chapterId = chapterDao.upsert(
            ChapterEntity(
                id = 1L,
                mangaId = mangaId,
                url = "chapter_url",
                name = "Chapter 1",
                read = false,
                chapterNumber = 1f
            )
        )

        // Upsert with no existing history
        readingHistoryDao.upsert(chapterId, readAt = 100L, readDurationMs = 1000L)

        // Verify insertion
        val history = readingHistoryDao.observeHistory().first()
        assertEquals(1, history.size)
        assertEquals(chapterId, history[0].chapterId)
        assertEquals(100L, history[0].readAt)
        assertEquals(1000L, history[0].readDurationMs)
    }

    @Test
    fun replaceHistory_insertsNewEntryWhenNoConflict() = runBlocking {
        // Setup: Insert test manga and chapter
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(
                id = 1L,
                title = "Test Manga",
                sourceId = 1L,
                url = "url",
                favorite = true
            )
        )
        val chapterId = chapterDao.upsert(
            ChapterEntity(
                id = 1L,
                mangaId = mangaId,
                url = "chapter_url",
                name = "Chapter 1",
                read = false,
                chapterNumber = 1f
            )
        )

        // Replace with no existing history (should insert)
        readingHistoryDao.replaceHistory(chapterId, readAt = 100L, readDurationMs = 1000L)

        // Verify insertion
        val history = readingHistoryDao.observeHistory().first()
        assertEquals(1, history.size)
        assertEquals(chapterId, history[0].chapterId)
        assertEquals(100L, history[0].readAt)
        assertEquals(1000L, history[0].readDurationMs)
    }

    @Test
    fun deleteHistoryForChapter_cascadesOnChapterDelete() = runBlocking {
        // Setup: Insert test manga and chapter
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(
                id = 1L,
                title = "Test Manga",
                sourceId = 1L,
                url = "url",
                favorite = true
            )
        )
        val chapter = ChapterEntity(
            id = 1L,
            mangaId = mangaId,
            url = "chapter_url",
            name = "Chapter 1",
            read = false,
            chapterNumber = 1f
        )
        val chapterId = chapterDao.upsert(chapter)

        // Insert history
        readingHistoryDao.upsert(chapterId, readAt = 100L, readDurationMs = 1000L)
        val historyBefore = readingHistoryDao.observeHistory().first()
        assertEquals(1, historyBefore.size)

        // Delete chapter (should cascade delete history due to foreign key)
        chapterDao.delete(chapter.copy(id = chapterId))

        // Verify history is deleted
        val historyAfter = readingHistoryDao.observeHistory().first()
        assertEquals(0, historyAfter.size)
    }

    /**
     * The scoped read behind the migration path (#1208): only the chapters asked for come back.
     *
     * The alternative on offer was `observeHistory`, which returns the whole app's history. This
     * test is what makes the narrowing real — with a query that ignored its argument it would still
     * find the row it wants, so it deliberately seeds a second chapter's history and asserts that
     * one is *absent*.
     */
    @Test
    fun getHistoryForChapters_returnsOnlyTheRequestedChapters() = runBlocking {
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(title = "Test Manga", sourceId = 1L, url = "url", favorite = true)
        )
        val wanted = chapterDao.upsert(
            ChapterEntity(mangaId = mangaId, url = "c1", name = "Chapter 1", chapterNumber = 1f)
        )
        val other = chapterDao.upsert(
            ChapterEntity(mangaId = mangaId, url = "c2", name = "Chapter 2", chapterNumber = 2f)
        )
        readingHistoryDao.upsert(wanted, readAt = 100L, readDurationMs = 1_000L)
        readingHistoryDao.upsert(other, readAt = 200L, readDurationMs = 2_000L)

        val history = readingHistoryDao.getHistoryForChapters(listOf(wanted))

        assertEquals(1, history.size)
        assertEquals(wanted, history.single().chapterId)
        assertEquals(100L, history.single().readAt)
        assertEquals(1_000L, history.single().readDurationMs)
    }

    /** A chapter nobody has opened simply has no row, so the result is shorter than the input. */
    @Test
    fun getHistoryForChapters_skipsChaptersWithNoHistory() = runBlocking {
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(title = "Test Manga", sourceId = 1L, url = "url", favorite = true)
        )
        val read = chapterDao.upsert(
            ChapterEntity(mangaId = mangaId, url = "c1", name = "Chapter 1", chapterNumber = 1f)
        )
        val unread = chapterDao.upsert(
            ChapterEntity(mangaId = mangaId, url = "c2", name = "Chapter 2", chapterNumber = 2f)
        )
        readingHistoryDao.upsert(read, readAt = 100L, readDurationMs = 1_000L)

        val history = readingHistoryDao.getHistoryForChapters(listOf(read, unread))

        assertEquals(listOf(read), history.map { it.chapterId })
    }

    /**
     * The batched replace used by migration: every entry lands, with replace semantics rather than
     * accumulation. Being `@Transaction` is what makes a failure partway through leave the history
     * as it was instead of half-rewritten — not observable from here, but the batching is.
     */
    @Test
    fun replaceHistoryAll_appliesEveryEntryWithoutAccumulating() = runBlocking {
        val mangaId = mangaDao.insertOrGetExisting(
            MangaEntity(title = "Test Manga", sourceId = 1L, url = "url", favorite = true)
        )
        val first = chapterDao.upsert(
            ChapterEntity(mangaId = mangaId, url = "c1", name = "Chapter 1", chapterNumber = 1f)
        )
        val second = chapterDao.upsert(
            ChapterEntity(mangaId = mangaId, url = "c2", name = "Chapter 2", chapterNumber = 2f)
        )
        // An existing row on one of them, so the replace has something to overwrite.
        readingHistoryDao.upsert(first, readAt = 1L, readDurationMs = 5_000L)

        readingHistoryDao.replaceHistoryAll(
            listOf(Triple(first, 100L, 1_000L), Triple(second, 200L, 2_000L)),
        )

        val byChapter = readingHistoryDao.observeHistory().first().associateBy { it.chapterId }
        assertEquals(2, byChapter.size)
        assertEquals(100L, byChapter.getValue(first).readAt)
        assertEquals("replaced, not added to the 5000 already there", 1_000L, byChapter.getValue(first).readDurationMs)
        assertEquals(2_000L, byChapter.getValue(second).readDurationMs)
    }
}
