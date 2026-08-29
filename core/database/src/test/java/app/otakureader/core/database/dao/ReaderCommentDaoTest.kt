package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.ReaderCommentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderCommentDaoTest {

    private lateinit var database: OtakuReaderDatabase
    private lateinit var readerCommentDao: ReaderCommentDao
    private lateinit var mangaDao: MangaDao
    private lateinit var chapterDao: ChapterDao

    private val mangaId = 1L
    private val chapterId = 10L

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java
        ).allowMainThreadQueries().build()
        readerCommentDao = database.readerCommentDao()
        mangaDao = database.mangaDao()
        chapterDao = database.chapterDao()

        runBlocking {
            mangaDao.insertOrGetExisting(MangaEntity(id = mangaId, title = "Test Manga", sourceId = 1L, url = "url", favorite = true))
            chapterDao.upsert(
                ChapterEntity(id = chapterId, mangaId = mangaId, url = "ch_url", name = "Chapter 1", read = false, chapterNumber = 1f)
            )
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun bookAndChapterScopesAreSeparate() = runBlocking {
        readerCommentDao.insert(ReaderCommentEntity(mangaId = mangaId, chapterId = null, body = "book thought"))
        readerCommentDao.insert(ReaderCommentEntity(mangaId = mangaId, chapterId = chapterId, body = "chapter thought"))

        val book = readerCommentDao.getBookComments(mangaId).first()
        val chapter = readerCommentDao.getChapterComments(chapterId).first()

        assertEquals(listOf("book thought"), book.map { it.body })
        assertEquals(listOf("chapter thought"), chapter.map { it.body })
    }

    @Test
    fun commentsAreOrderedOldestFirst() = runBlocking {
        readerCommentDao.insert(ReaderCommentEntity(mangaId = mangaId, body = "second", createdAt = 2_000L))
        readerCommentDao.insert(ReaderCommentEntity(mangaId = mangaId, body = "first", createdAt = 1_000L))

        val book = readerCommentDao.getBookComments(mangaId).first()
        assertEquals(listOf("first", "second"), book.map { it.body })
    }

    @Test
    fun deleteByIdRemovesComment() = runBlocking {
        val id = readerCommentDao.insert(ReaderCommentEntity(mangaId = mangaId, body = "to delete"))

        readerCommentDao.deleteById(id)

        assertTrue(readerCommentDao.getBookComments(mangaId).first().isEmpty())
    }

    @Test
    fun deletingChapterCascadesChapterCommentsOnly() = runBlocking {
        readerCommentDao.insert(ReaderCommentEntity(mangaId = mangaId, chapterId = chapterId, body = "chapter"))
        readerCommentDao.insert(ReaderCommentEntity(mangaId = mangaId, chapterId = null, body = "book"))

        chapterDao.deleteByMangaId(mangaId)

        assertTrue(readerCommentDao.getChapterComments(chapterId).first().isEmpty())
        assertEquals(1, readerCommentDao.getBookComments(mangaId).first().size)
    }

    @Test
    fun deletingMangaCascadesAllComments() = runBlocking {
        readerCommentDao.insert(ReaderCommentEntity(mangaId = mangaId, chapterId = chapterId, body = "chapter"))
        readerCommentDao.insert(ReaderCommentEntity(mangaId = mangaId, chapterId = null, body = "book"))

        mangaDao.deleteById(mangaId)

        assertTrue(readerCommentDao.getBookComments(mangaId).first().isEmpty())
        assertTrue(readerCommentDao.getChapterComments(chapterId).first().isEmpty())
    }
}
