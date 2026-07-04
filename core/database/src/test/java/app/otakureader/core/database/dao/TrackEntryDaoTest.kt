package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.TrackEntryEntity
import kotlinx.coroutines.test.runTest
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
    fun getTrackerStats_countsDistinctMangaAndServices() = runTest {
        // Manga 1 tracked on two services counts once as a manga, twice as services;
        // two manga on tracker 1 count as one service.
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 1))
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 2))
        trackEntryDao.upsert(makeEntry(mangaId = 2L, trackerId = 1))

        trackEntryDao.getTrackerStats().test {
            val stats = awaitItem()
            assertEquals(2, stats.trackedMangaCount)
            assertEquals(2, stats.serviceCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getTrackerStats_emptyTableReturnsZeros() = runTest {
        trackEntryDao.getTrackerStats().test {
            val stats = awaitItem()
            assertEquals(0, stats.trackedMangaCount)
            assertEquals(0, stats.serviceCount)
            assertNull(stats.meanScore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getTrackerStats_meanScoreIgnoresUnscoredEntries() = runTest {
        // score 0 = unscored — must not drag the mean down
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 1, score = 8f))
        trackEntryDao.upsert(makeEntry(mangaId = 2L, trackerId = 1, score = 6f))
        trackEntryDao.upsert(makeEntry(mangaId = 3L, trackerId = 1, score = 0f))

        trackEntryDao.getTrackerStats().test {
            assertEquals(7f, awaitItem().meanScore!!, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getTrackerStats_meanScoreNullWhenNothingScored() = runTest {
        trackEntryDao.upsert(makeEntry(mangaId = 1L, trackerId = 1, score = 0f))

        trackEntryDao.getTrackerStats().test {
            assertNull(awaitItem().meanScore)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
