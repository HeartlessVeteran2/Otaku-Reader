package app.otakureader.data.repository

import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MangaRepositoryImplTest {

    private lateinit var mangaDao: MangaDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var repository: MangaRepositoryImpl

    private fun makeEntity(
        id: Long = 1L,
        title: String = "Manga $id",
        favorite: Boolean = false,
        status: Int = MangaStatus.ONGOING.ordinal,
        genre: String? = "Action|||Adventure"
    ) = MangaEntity(
        id = id,
        sourceId = 1L,
        url = "/m/$id",
        title = title,
        favorite = favorite,
        status = status,
        genre = genre
    )

    @Before
    fun setUp() {
        mangaDao = mockk()
        chapterDao = mockk()
        repository = MangaRepositoryImpl(mangaDao, chapterDao)
    }

    // ---- getLibraryManga ----

    @Test
    fun getLibraryManga_returnsFavoritesMapped() = runTest {
        val entities = listOf(
            app.otakureader.core.database.entity.MangaWithUnreadCount(makeEntity(1L, favorite = true), 0),
            app.otakureader.core.database.entity.MangaWithUnreadCount(makeEntity(2L, favorite = true), 0)
        )
        every { mangaDao.getFavoriteMangaWithUnreadCount() } returns flowOf(entities)

        repository.getLibraryManga().test {
            val mangas = awaitItem()
            assertEquals(2, mangas.size)
            assertTrue(mangas.all { it.favorite })
            awaitComplete()
        }
    }

    @Test
    fun getLibraryManga_withEmptyFavorites_emitsEmptyList() = runTest {
        every { mangaDao.getFavoriteMangaWithUnreadCount() } returns flowOf(emptyList())

        repository.getLibraryManga().test {
            assertEquals(emptyList<Manga>(), awaitItem())
            awaitComplete()
        }
    }

    // ---- getMangaById ----

    @Test
    fun getMangaById_existingId_returnsMappedManga() = runTest {
        val entity = makeEntity(5L, title = "Naruto")
        coEvery { mangaDao.getMangaById(5L) } returns entity

        val manga = repository.getMangaById(5L)

        assertEquals(5L, manga?.id)
        assertEquals("Naruto", manga?.title)
    }

    @Test
    fun getMangaById_missingId_returnsNull() = runTest {
        coEvery { mangaDao.getMangaById(999L) } returns null

        val manga = repository.getMangaById(999L)

        assertNull(manga)
    }

    // ---- getMangaByIdFlow ----

    @Test
    fun getMangaByIdFlow_combinesWithUnreadCount() = runTest {
        val entity = makeEntity(1L)
        every { mangaDao.getMangaByIdFlow(1L) } returns flowOf(entity)
        every { chapterDao.getUnreadCountByMangaId(1L) } returns flowOf(7)

        repository.getMangaByIdFlow(1L).test {
            val manga = awaitItem()
            assertEquals(1L, manga?.id)
            assertEquals(7, manga?.unreadCount)
            awaitComplete()
        }
    }

    @Test
    fun getMangaByIdFlow_whenMangaNull_emitsNull() = runTest {
        every { mangaDao.getMangaByIdFlow(999L) } returns flowOf(null)
        every { chapterDao.getUnreadCountByMangaId(999L) } returns flowOf(0)

        repository.getMangaByIdFlow(999L).test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }

    // ---- genre mapping ----

    @Test
    fun getLibraryManga_genreString_splitIntoList() = runTest {
        val entity = app.otakureader.core.database.entity.MangaWithUnreadCount(makeEntity(1L, genre = "Action|||Adventure|||Fantasy"), 0)
        every { mangaDao.getFavoriteMangaWithUnreadCount() } returns flowOf(listOf(entity))

        repository.getLibraryManga().test {
            val manga = awaitItem().first()
            assertEquals(listOf("Action", "Adventure", "Fantasy"), manga.genre)
            awaitComplete()
        }
    }

    @Test
    fun getLibraryManga_nullGenre_returnsEmptyList() = runTest {
        val entity = app.otakureader.core.database.entity.MangaWithUnreadCount(makeEntity(1L, genre = null), 0)
        every { mangaDao.getFavoriteMangaWithUnreadCount() } returns flowOf(listOf(entity))

        repository.getLibraryManga().test {
            val manga = awaitItem().first()
            assertEquals(emptyList<String>(), manga.genre)
            awaitComplete()
        }
    }

    // ---- searchLibraryManga ----

    @Test
    fun searchLibraryManga_delegatesToSearchFavoriteManga() = runTest {
        val entity = makeEntity(1L, title = "Naruto")
        every { mangaDao.searchFavoriteManga("Naru") } returns flowOf(listOf(entity))

        repository.searchLibraryManga("Naru").test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("Naruto", results.first().title)
            awaitComplete()
        }
    }

    @Test
    fun searchLibraryManga_withNoMatches_returnsEmptyList() = runTest {
        every { mangaDao.searchFavoriteManga("xyz") } returns flowOf(emptyList())

        repository.searchLibraryManga("xyz").test {
            assertEquals(emptyList<app.otakureader.domain.model.Manga>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun insertManga_returnsInsertedId() = runTest {
        val manga = Manga(id = 0L, sourceId = 1L, url = "/m/new", title = "New Manga")
        coEvery { mangaDao.insert(any()) } returns 42L

        val id = repository.insertManga(manga)

        assertEquals(42L, id)
    }

    // ---- updateManga ----

    @Test
    fun updateManga_callsDaoWithEntity() = runTest {
        val manga = Manga(id = 1L, sourceId = 1L, url = "/m/1", title = "Updated")
        coEvery { mangaDao.update(any()) } returns Unit

        repository.updateManga(manga)

        coVerify { mangaDao.update(match { it.id == 1L && it.title == "Updated" }) }
    }

    // ---- deleteManga ----

    @Test
    fun deleteManga_callsDaoDeleteById() = runTest {
        coEvery { mangaDao.deleteById(1L) } returns Unit

        repository.deleteManga(1L)

        coVerify { mangaDao.deleteById(1L) }
    }

    // ---- toggleFavorite ----

    @Test
    fun toggleFavorite_fromFalse_setsTrue() = runTest {
        val entity = makeEntity(1L, favorite = false)
        coEvery { mangaDao.getMangaById(1L) } returns entity
        coEvery { mangaDao.updateFavorite(1L, true) } returns Unit

        repository.toggleFavorite(1L)

        coVerify { mangaDao.updateFavorite(1L, true) }
    }

    @Test
    fun toggleFavorite_fromTrue_setsFalse() = runTest {
        val entity = makeEntity(1L, favorite = true)
        coEvery { mangaDao.getMangaById(1L) } returns entity
        coEvery { mangaDao.updateFavorite(1L, false) } returns Unit

        repository.toggleFavorite(1L)

        coVerify { mangaDao.updateFavorite(1L, false) }
    }

    @Test
    fun toggleFavorite_withNonExistentId_doesNothing() = runTest {
        coEvery { mangaDao.getMangaById(999L) } returns null

        repository.toggleFavorite(999L)

        coVerify(exactly = 0) { mangaDao.updateFavorite(any(), any()) }
    }

    // ---- isFavorite ----

    @Test
    fun isFavorite_delegatesToDao() = runTest {
        every { mangaDao.isFavorite(1L) } returns flowOf(true)

        repository.isFavorite(1L).test {
            assertTrue(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun isFavorite_whenNotFavorite_returnsFalse() = runTest {
        every { mangaDao.isFavorite(2L) } returns flowOf(false)

        repository.isFavorite(2L).test {
            assertFalse(awaitItem())
            awaitComplete()
        }
    }
}
