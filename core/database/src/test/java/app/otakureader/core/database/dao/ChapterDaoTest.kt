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
class ChapterDaoTest {

    private lateinit var database: OtakuReaderDatabase
    private lateinit var chapterDao: ChapterDao
    private lateinit var mangaDao: MangaDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java
        ).allowMainThreadQueries().build()
        chapterDao = database.chapterDao()
        mangaDao = database.mangaDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun updateChapterProgress_marksSingleChapterAsRead() = runBlocking {
        val mangaId = 1L
        mangaDao.insert(MangaEntity(id = mangaId, title = "Test Manga", sourceId = 1L, url = "url", favorite = true))

        val chapter = ChapterEntity(id = 1L, mangaId = mangaId, url = "url_1", name = "Chapter 1", read = false, chapterNumber = 1f)
        chapterDao.insert(chapter)

        chapterDao.updateChapterProgress(1L, read = true, lastPageRead = 5)

        val updated = chapterDao.getChapterById(1L)
        assertEquals(true, updated?.read)
        assertEquals(5, updated?.lastPageRead)
    }

    @Test
    fun updateBatchChapterProgress_marksAllChaptersAsRead() = runBlocking {
        val mangaId = 2L
        mangaDao.insert(MangaEntity(id = mangaId, title = "Test Manga 2", sourceId = 1L, url = "url2", favorite = true))

        val chapters = (1..10).map { i ->
            ChapterEntity(id = i.toLong() + 100, mangaId = mangaId, url = "url_$i", name = "Chapter $i", read = false, chapterNumber = i.toFloat())
        }
        chapterDao.insertAll(chapters)

        val chapterIds = chapters.map { it.id }
        chapterDao.updateChapterProgress(chapterIds, read = true, lastPageRead = 0)

        val unreadCount = chapterDao.getUnreadCountByMangaId(mangaId).first()
        assertEquals(0, unreadCount)

        val readCount = chapterDao.getReadCountByMangaId(mangaId).first()
        assertEquals(10, readCount)
    }

    @Test
    fun updateBatchChapterProgress_onlyUpdatesSpecifiedChapters() = runBlocking {
        val mangaId = 3L
        mangaDao.insert(MangaEntity(id = mangaId, title = "Test Manga 3", sourceId = 1L, url = "url3", favorite = true))

        val chapters = (1..5).map { i ->
            ChapterEntity(id = i.toLong() + 200, mangaId = mangaId, url = "url_$i", name = "Chapter $i", read = false, chapterNumber = i.toFloat())
        }
        chapterDao.insertAll(chapters)

        // Only update the first 3
        val idsToUpdate = chapters.take(3).map { it.id }
        chapterDao.updateChapterProgress(idsToUpdate, read = true, lastPageRead = 0)

        val unreadCount = chapterDao.getUnreadCountByMangaId(mangaId).first()
        assertEquals(2, unreadCount)

        val readCount = chapterDao.getReadCountByMangaId(mangaId).first()
        assertEquals(3, readCount)
    }

}
