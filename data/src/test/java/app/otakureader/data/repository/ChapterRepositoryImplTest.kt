package app.otakureader.data.repository

import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.ChapterWithHistoryEntity
import app.otakureader.core.database.entity.ReadingHistoryEntity
import app.otakureader.domain.model.Chapter
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ChapterRepositoryImplTest {

    private lateinit var chapterDao: ChapterDao
    private lateinit var repository: ChapterRepositoryImpl

    private fun makeEntity(
        id: Long = 1L,
        mangaId: Long = 10L,
        url: String = "/c/$id",
        name: String = "Chapter $id",
        read: Boolean = false,
        bookmark: Boolean = false,
        lastPageRead: Int = 0,
        chapterNumber: Float = id.toFloat()
    ) = ChapterEntity(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        chapterNumber = chapterNumber
    )

    @Before
    fun setUp() {
        chapterDao = mockk()
        repository = ChapterRepositoryImpl(chapterDao)
    }

    // ---- getChaptersByMangaId ----

    @Test
    fun getChaptersByMangaId_returnsMappedChapters() = runTest {
        val mangaId = 10L
        val entities = listOf(makeEntity(1L, mangaId), makeEntity(2L, mangaId))
        every { chapterDao.getChaptersByMangaId(mangaId) } returns flowOf(entities)

        repository.getChaptersByMangaId(mangaId).test {
            val chapters = awaitItem()
            assertEquals(2, chapters.size)
            assertEquals(1L, chapters[0].id)
            assertEquals(2L, chapters[1].id)
            awaitComplete()
        }
    }

    @Test
    fun getChaptersByMangaId_withEmptyResult_emitsEmptyList() = runTest {
        val mangaId = 99L
        every { chapterDao.getChaptersByMangaId(mangaId) } returns flowOf(emptyList())

        repository.getChaptersByMangaId(mangaId).test {
            assertEquals(emptyList<Chapter>(), awaitItem())
            awaitComplete()
        }
    }

    // ---- getChapterById ----

    @Test
    fun getChapterById_existingId_returnsMappedChapter() = runTest {
        val entity = makeEntity(5L)
        coEvery { chapterDao.getChapterById(5L) } returns entity

        val chapter = repository.getChapterById(5L)

        assertEquals(5L, chapter?.id)
        assertEquals(entity.name, chapter?.name)
    }

    @Test
    fun getChapterById_missingId_returnsNull() = runTest {
        coEvery { chapterDao.getChapterById(999L) } returns null

        val chapter = repository.getChapterById(999L)

        assertNull(chapter)
    }

    // ---- getNextUnreadChapter ----

    @Test
    fun getNextUnreadChapter_withUnreadChapter_returnsMapped() = runTest {
        val mangaId = 10L
        val entity = makeEntity(3L, mangaId, read = false)
        coEvery { chapterDao.getNextUnreadChapter(mangaId) } returns entity

        val chapter = repository.getNextUnreadChapter(mangaId)

        assertEquals(3L, chapter?.id)
        assertEquals(false, chapter?.read)
    }

    @Test
    fun getNextUnreadChapter_allRead_returnsNull() = runTest {
        val mangaId = 10L
        coEvery { chapterDao.getNextUnreadChapter(mangaId) } returns null

        val chapter = repository.getNextUnreadChapter(mangaId)

        assertNull(chapter)
    }

    // ---- updateChapterProgress ----

    @Test
    fun updateChapterProgress_callsDaoWithCorrectArgs() = runTest {
        coEvery { chapterDao.updateChapterProgress(any(), any(), any()) } returns Unit

        repository.updateChapterProgress(chapterId = 7L, read = true, lastPageRead = 10)

        coVerify { chapterDao.updateChapterProgress(7L, true, 10) }
    }

    // ---- updateBookmark ----

    @Test
    fun updateBookmark_callsDaoWithCorrectArgs() = runTest {
        coEvery { chapterDao.updateBookmark(any(), any()) } returns Unit

        repository.updateBookmark(chapterId = 2L, bookmark = true)

        coVerify { chapterDao.updateBookmark(2L, true) }
    }

    // ---- insertChapters ----

    @Test
    fun insertChapters_insertsAllConvertedEntities() = runTest {
        coEvery { chapterDao.insertAll(any()) } returns Unit

        val chapters = listOf(
            Chapter(id = 1L, mangaId = 10L, url = "/c/1", name = "Ch 1"),
            Chapter(id = 2L, mangaId = 10L, url = "/c/2", name = "Ch 2")
        )

        repository.insertChapters(chapters)

        coVerify {
            chapterDao.insertAll(match { entities ->
                entities.size == 2 &&
                    entities[0].id == 1L &&
                    entities[1].id == 2L
            })
        }
    }

    // ---- getUnreadCountByMangaId ----

    @Test
    fun getUnreadCountByMangaId_delegatesToDao() = runTest {
        val mangaId = 10L
        every { chapterDao.getUnreadCountByMangaId(mangaId) } returns flowOf(5)

        repository.getUnreadCountByMangaId(mangaId).test {
            assertEquals(5, awaitItem())
            awaitComplete()
        }
    }

    // ---- observeHistory ----

    @Test
    fun observeHistory_throwsNotImplementedError() {
        try {
            repository.observeHistory()
            throw AssertionError("Expected NotImplementedError to be thrown")
        } catch (e: NotImplementedError) {
            // expected — history requires ReadingHistoryDao join query (TODO)
        }
    }

    // ---- recordHistory ----

    @Test
    fun recordHistory_upsertsHistoryEntity() = runTest {
        coEvery { readingHistoryDao.upsert(any()) } returns Unit

        repository.recordHistory(chapterId = 5L, readAt = 2000L, readDurationMs = 30_000L)

        coVerify {
            readingHistoryDao.upsert(match { entity ->
                entity.chapterId == 5L &&
                    entity.readAt == 2000L &&
                    entity.readDurationMs == 30_000L
            })
        }
    }

    // ---- removeFromHistory ----

    @Test
    fun removeFromHistory_callsDaoDeleteForChapter() = runTest {
        coEvery { readingHistoryDao.deleteHistoryForChapter(any()) } returns Unit

        repository.removeFromHistory(chapterId = 3L)

        coVerify { readingHistoryDao.deleteHistoryForChapter(3L) }
    }

    // ---- clearAllHistory ----

    @Test
    fun clearAllHistory_callsDaoDeleteAll() = runTest {
        coEvery { readingHistoryDao.deleteAll() } returns Unit

        repository.clearAllHistory()

        coVerify { readingHistoryDao.deleteAll() }
    }

    // ---- recordHistory ----

    @Test
    fun recordHistory_upsertsHistoryEntity() = runTest {
        coEvery { readingHistoryDao.upsert(any()) } returns Unit

        repository.recordHistory(chapterId = 5L, readAt = 2000L, readDurationMs = 30_000L)

        coVerify {
            readingHistoryDao.upsert(match { entity ->
                entity.chapterId == 5L &&
                    entity.readAt == 2000L &&
                    entity.readDurationMs == 30_000L
            })
        }
    }

    // ---- removeFromHistory ----

    @Test
    fun removeFromHistory_callsDaoDeleteForChapter() = runTest {
        coEvery { readingHistoryDao.deleteHistoryForChapter(any()) } returns Unit

        repository.removeFromHistory(chapterId = 3L)

        coVerify { readingHistoryDao.deleteHistoryForChapter(3L) }
    }

    // ---- clearAllHistory ----

    @Test
    fun clearAllHistory_callsDaoDeleteAll() = runTest {
        coEvery { readingHistoryDao.deleteAll() } returns Unit

        repository.clearAllHistory()

        coVerify { readingHistoryDao.deleteAll() }
    }

    // ---- mapping ----

    @Test
    fun chapterEntityMapping_preservesAllFields() = runTest {
        val entity = ChapterEntity(
            id = 99L,
            mangaId = 5L,
            url = "/chapter/99",
            name = "Chapter 99",
            scanlator = "Team Scan",
            read = true,
            bookmark = true,
            lastPageRead = 15,
            chapterNumber = 99.5f,
            dateUpload = 12345L
        )
        every { chapterDao.getChaptersByMangaId(5L) } returns flowOf(listOf(entity))

        repository.getChaptersByMangaId(5L).test {
            val chapter = awaitItem().first()
            assertEquals(entity.id, chapter.id)
            assertEquals(entity.mangaId, chapter.mangaId)
            assertEquals(entity.url, chapter.url)
            assertEquals(entity.name, chapter.name)
            assertEquals(entity.scanlator, chapter.scanlator)
            assertEquals(entity.read, chapter.read)
            assertEquals(entity.bookmark, chapter.bookmark)
            assertEquals(entity.lastPageRead, chapter.lastPageRead)
            assertEquals(entity.chapterNumber, chapter.chapterNumber)
            assertEquals(entity.dateUpload, chapter.dateUpload)
            awaitComplete()
        }
    }
}
