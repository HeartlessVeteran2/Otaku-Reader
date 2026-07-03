package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.UpdateErrorEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateErrorDaoTest {

    private lateinit var database: OtakuReaderDatabase
    private lateinit var updateErrorDao: UpdateErrorDao
    private lateinit var mangaDao: MangaDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java
        ).allowMainThreadQueries().build()
        updateErrorDao = database.updateErrorDao()
        mangaDao = database.mangaDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun upsert_replacesPreviousErrorForSameManga() = runBlocking {
        val mangaId = 1L
        mangaDao.insert(MangaEntity(id = mangaId, title = "Test Manga", sourceId = 1L, url = "url", favorite = true))

        updateErrorDao.upsert(UpdateErrorEntity(mangaId = mangaId, errorMessage = "First failure", timestamp = 1L))
        updateErrorDao.upsert(UpdateErrorEntity(mangaId = mangaId, errorMessage = "Second failure", timestamp = 2L))

        val errors = updateErrorDao.observeErrors().first()
        assertEquals(1, errors.size)
        assertEquals("Second failure", errors.first().error.errorMessage)
    }

    @Test
    fun observeErrors_joinsMangaTitleAndOrdersNewestFirst() = runBlocking {
        val manga1 = 1L
        val manga2 = 2L
        mangaDao.insert(MangaEntity(id = manga1, title = "Older Failure Manga", sourceId = 1L, url = "url1", favorite = true))
        mangaDao.insert(MangaEntity(id = manga2, title = "Newer Failure Manga", sourceId = 1L, url = "url2", favorite = true))

        updateErrorDao.upsert(UpdateErrorEntity(mangaId = manga1, errorMessage = "Old error", timestamp = 1_000L))
        updateErrorDao.upsert(UpdateErrorEntity(mangaId = manga2, errorMessage = "New error", timestamp = 2_000L))

        val errors = updateErrorDao.observeErrors().first()
        assertEquals(2, errors.size)
        assertEquals("Newer Failure Manga", errors.first().manga.title)
        assertEquals("Older Failure Manga", errors.last().manga.title)
    }

    @Test
    fun deleteByMangaId_removesOnlyThatMangasError() = runBlocking {
        val manga1 = 1L
        val manga2 = 2L
        mangaDao.insert(MangaEntity(id = manga1, title = "Manga 1", sourceId = 1L, url = "url1", favorite = true))
        mangaDao.insert(MangaEntity(id = manga2, title = "Manga 2", sourceId = 1L, url = "url2", favorite = true))
        updateErrorDao.upsert(UpdateErrorEntity(mangaId = manga1, errorMessage = "Error 1", timestamp = 1L))
        updateErrorDao.upsert(UpdateErrorEntity(mangaId = manga2, errorMessage = "Error 2", timestamp = 2L))

        updateErrorDao.deleteByMangaId(manga1)

        val errors = updateErrorDao.observeErrors().first()
        assertEquals(1, errors.size)
        assertEquals(manga2, errors.first().error.mangaId)
    }

    @Test
    fun deleteAll_clearsEveryError() = runBlocking {
        val manga1 = 1L
        val manga2 = 2L
        mangaDao.insert(MangaEntity(id = manga1, title = "Manga 1", sourceId = 1L, url = "url1", favorite = true))
        mangaDao.insert(MangaEntity(id = manga2, title = "Manga 2", sourceId = 1L, url = "url2", favorite = true))
        updateErrorDao.upsert(UpdateErrorEntity(mangaId = manga1, errorMessage = "Error 1", timestamp = 1L))
        updateErrorDao.upsert(UpdateErrorEntity(mangaId = manga2, errorMessage = "Error 2", timestamp = 2L))

        updateErrorDao.deleteAll()

        assertTrue(updateErrorDao.observeErrors().first().isEmpty())
    }
}
