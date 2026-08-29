package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.PageBookmarkEntity
import app.otakureader.core.database.entity.TrackEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards `MangaDao.insertOrGetExisting` against the id reassignment that `OnConflictStrategy.REPLACE`
 * caused (#1269).
 *
 * The stakes are why these tests assert so much state. `manga` is the parent of eleven cascading
 * tables, and one of them — `chapters` — is itself the parent of three more, so a REPLACE that
 * reassigned the manga's id took the chapter list, every read marker, every page bookmark, the
 * user's chapter notes, both tracker tables, categories, reading-list membership and the AniList
 * link with it. Asserting only the returned id would have missed all of that: the call still
 * returned a plausible `Long`.
 */
@RunWith(AndroidJUnit4::class)
class MangaInsertTest {

    private lateinit var database: OtakuReaderDatabase
    private lateinit var mangaDao: MangaDao
    private lateinit var chapterDao: ChapterDao

    private val sourceId = 42L
    private val url = "/manga/one-piece"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        mangaDao = database.mangaDao()
        chapterDao = database.chapterDao()
    }

    @After
    fun tearDown() = database.close()

    private fun manga(
        title: String = "One Piece",
        author: String? = "Oda",
        favorite: Boolean = true,
        initialized: Boolean = true,
    ) = MangaEntity(
        sourceId = sourceId,
        url = url,
        title = title,
        author = author,
        favorite = favorite,
        initialized = initialized,
        notes = "my notes",
        readerMode = 2,
    )

    /**
     * The stub an insert actually races against. `SourceMangaDetailViewModel` builds one of these
     * when a manga is opened from Browse and no local row is found yet: a title, and nothing else.
     */
    private fun stub() = MangaEntity(
        sourceId = sourceId,
        url = url,
        title = url,
        initialized = false,
    )

    @Test
    fun `re-inserting the same manga keeps its id`() = runBlocking {
        val first = mangaDao.insertOrGetExisting(manga())

        val second = mangaDao.insertOrGetExisting(manga())

        assertEquals(first, second)
        // And the row itself, not just the returned value — a REPLACE would have left a different
        // id behind while still returning something plausible.
        assertEquals(first, mangaDao.getMangaBySourceAndUrl(sourceId, url)?.id)
        assertEquals(1, mangaDao.getAllMangaOnce().size)
    }

    /**
     * The damage the issue is actually about, end to end and two levels deep: the chapter survives
     * because the manga was not deleted, and the history and bookmark survive because the chapter
     * was not deleted.
     */
    @Test
    fun `re-inserting does not destroy chapters, history or bookmarks`() = runBlocking {
        val mangaId = mangaDao.insertOrGetExisting(manga())
        val chapterId = chapterDao.upsert(
            ChapterEntity(mangaId = mangaId, url = "/c/1", name = "Chapter 1", read = true, lastPageRead = 12),
        )
        database.readingHistoryDao().replaceHistory(chapterId, readAt = 1_000L, readDurationMs = 5_000L)
        database.pageBookmarkDao().insertBookmark(
            PageBookmarkEntity(chapterId = chapterId, mangaId = mangaId, pageIndex = 3),
        )
        database.trackEntryDao().upsert(
            TrackEntryEntity(
                mangaId = mangaId, trackerId = 1, remoteId = 99, title = "One Piece",
                status = 1, lastChapterRead = 1f, totalChapters = 0, score = 0f,
                startDate = 0, finishDate = 0,
            ),
        )

        mangaDao.insertOrGetExisting(manga())

        val chapters = chapterDao.getChaptersByMangaIdOnce(mangaId)
        assertEquals("the chapter must outlive a conflicting insert", 1, chapters.size)
        assertEquals("read state must survive", true, chapters.single().read)
        assertEquals(12, chapters.single().lastPageRead)
        assertEquals(
            "reading history cascades from chapters, so it dies too if the manga is replaced",
            1,
            database.readingHistoryDao().observeHistory().first().size,
        )
        assertEquals(1, database.pageBookmarkDao().getBookmarksForManga(mangaId).first().size)
        assertEquals(1, database.trackEntryDao().getByMangaId(mangaId).first().size)
    }

    /**
     * The losing racer is usually a stub, and a stub knows almost nothing. Nothing it carries may
     * overwrite what the real row already holds — this is the `sourceOrder` trap from #1254, where
     * a default value in an incoming row silently erased a real one.
     */
    @Test
    fun `a conflicting stub cannot overwrite real metadata`() = runBlocking {
        val mangaId = mangaDao.insertOrGetExisting(manga())

        val returned = mangaDao.insertOrGetExisting(stub())

        assertEquals("the stub must resolve to the existing row", mangaId, returned)
        val row = mangaDao.getMangaBySourceAndUrl(sourceId, url)!!
        assertEquals("One Piece", row.title)
        assertEquals("Oda", row.author)
        assertTrue("initialized must not be reset by a stub", row.initialized)
        assertTrue("favorite is the user's, not the inserter's", row.favorite)
        assertEquals("my notes", row.notes)
        assertEquals(2, row.readerMode)
    }

    /** A genuinely new manga still inserts, and gets its own id. */
    @Test
    fun `a different url inserts a new row`() = runBlocking {
        val first = mangaDao.insertOrGetExisting(manga())

        val second = mangaDao.insertOrGetExisting(manga().copy(url = "/manga/naruto"))

        assertTrue("a distinct manga must get its own id", first != second)
        assertEquals(2, mangaDao.getAllMangaOnce().size)
    }
}
