package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.TrackEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackEntryDaoTest {

    private lateinit var database: OtakuReaderDatabase
    private lateinit var trackEntryDao: TrackEntryDao

    private fun makeEntry(mangaId: Long, trackerId: Int, score: Float = 0f) = TrackEntryEntity(
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = mangaId * 100 + trackerId,
        title = "Manga $mangaId",
        status = 1,
        lastChapterRead = 0f,
        totalChapters = 0,
        score = score,
        startDate = 0L,
        finishDate = 0L,
    )

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java
        ).allowMainThreadQueries().build()
        trackEntryDao = database.trackEntryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getTrackedMangaCount_countsDistinctManga() = runBlocking {
        // Manga 1 tracked on two services still counts once
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 1))
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 2))
        trackEntryDao.upsert(makeEntry(mangaId = 2L, trackerId = 1))

        assertEquals(2, trackEntryDao.getTrackedMangaCount().first())
    }

    @Test
    fun getTrackedMangaCount_emptyTableReturnsZero() = runBlocking {
        assertEquals(0, trackEntryDao.getTrackedMangaCount().first())
    }

    @Test
    fun getMeanScore_ignoresUnscoredEntries() = runBlocking {
        // score 0 = unscored — must not drag the mean down
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 1, score = 8f))
        trackEntryDao.upsert(makeEntry(mangaId = 2L, trackerId = 1, score = 6f))
        trackEntryDao.upsert(makeEntry(mangaId = 3L, trackerId = 1, score = 0f))

        assertEquals(7f, trackEntryDao.getMeanScore().first()!!, 0.001f)
    }

    @Test
    fun getMeanScore_nullWhenNothingScored() = runBlocking {
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 1, score = 0f))

        assertNull(trackEntryDao.getMeanScore().first())
    }

    @Test
    fun getTrackerServiceCount_countsDistinctServices() = runBlocking {
        // Two manga on tracker 1 counts as one service
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 1))
        trackEntryDao.upsert(makeEntry(mangaId = 2L, trackerId = 1))
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 3))

        assertEquals(2, trackEntryDao.getTrackerServiceCount().first())
    }
}
