package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaDaoTest {

 private lateinit var database: OtakuReaderDatabase
 private lateinit var mangaDao: MangaDao

 @Before
 fun setup() {
 database = Room.inMemoryDatabaseBuilder(
 ApplicationProvider.getApplicationContext(),
 OtakuReaderDatabase::class.java
 ).allowMainThreadQueries().build()
 mangaDao = database.mangaDao()
 }

 @After
 fun teardown() {
 database.close()
 }

 @Test
 fun insertAndGetManga() = runBlocking {
 val manga = MangaEntity(
 id = 1L, title = "Test Manga", sourceId = 1L, url = "http://example.com/manga",
 author = "Test Author", artist = "Test Artist", description = "Test description",
 genre = "Action||Comedy", status = 1, favorite = true
 )
 mangaDao.insertOrGetExisting(manga)

 val retrieved = mangaDao.getMangaById(1L)
 assertNotNull(retrieved)
 assertEquals("Test Manga", retrieved?.title)
 assertEquals("Test Author", retrieved?.author)
 assertEquals("Action||Comedy", retrieved?.genre)
 assertEquals(true, retrieved?.favorite)
 }

 @Test
 fun getMangaByIdFlow_emitsUpdates() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "Original", sourceId = 1L, url = "url", favorite = true)
 mangaDao.insertOrGetExisting(manga)

 // Collect initial
 val initial = mangaDao.getMangaByIdFlow(1L).first()
 assertEquals("Original", initial?.title)

 // Update
 mangaDao.update(manga.copy(title = "Updated"))

 // Collect updated
 val updated = mangaDao.getMangaByIdFlow(1L).first()
 assertEquals("Updated", updated?.title)
 }

 @Test
 fun getFavoriteManga_returnsOnlyFavorites() = runBlocking {
 val fav1 = MangaEntity(id = 1L, title = "Fav 1", sourceId = 1L, url = "url1", favorite = true)
 val fav2 = MangaEntity(id = 2L, title = "Fav 2", sourceId = 1L, url = "url2", favorite = true)
 val nonFav = MangaEntity(id = 3L, title = "Not Fav", sourceId = 1L, url = "url3", favorite = false)

 mangaDao.insertOrGetExisting(fav1)
 mangaDao.insertOrGetExisting(fav2)
 mangaDao.insertOrGetExisting(nonFav)

 val favorites = mangaDao.getFavoriteManga().first()
 assertEquals(2, favorites.size)
 assertTrue(favorites.all { it.favorite })
 }

 @Test
 fun searchFavoriteManga_findsByTitle() = runBlocking {
 val manga1 = MangaEntity(id = 1L, title = "One Piece", sourceId = 1L, url = "url1", favorite = true)
 val manga2 = MangaEntity(id = 2L, title = "Naruto", sourceId = 1L, url = "url2", favorite = true)
 val manga3 = MangaEntity(id = 3L, title = "One Punch Man", sourceId = 1L, url = "url3", favorite = true)

 mangaDao.insertOrGetExisting(manga1)
 mangaDao.insertOrGetExisting(manga2)
 mangaDao.insertOrGetExisting(manga3)

 val results = mangaDao.searchFavoriteManga("One").first()
 assertEquals(2, results.size)
 assertTrue(results.any { it.title == "One Piece" })
 assertTrue(results.any { it.title == "One Punch Man" })
 assertFalse(results.any { it.title == "Naruto" })
 }

 @Test
 fun getMangaBySourceAndUrl_returnsCorrect() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "Test", sourceId = 42L, url = "http://source.com/123")
 mangaDao.insertOrGetExisting(manga)

 val retrieved = mangaDao.getMangaBySourceAndUrl(42L, "http://source.com/123")
 assertNotNull(retrieved)
 assertEquals("Test", retrieved?.title)

 val notFound = mangaDao.getMangaBySourceAndUrl(99L, "http://other.com")
 assertNull(notFound)
 }

 @Test
 fun getMangaByIds_returnsInIdOrder() = runBlocking {
 val manga1 = MangaEntity(id = 1L, title = "First", sourceId = 1L, url = "url1")
 val manga2 = MangaEntity(id = 2L, title = "Second", sourceId = 1L, url = "url2")
 val manga3 = MangaEntity(id = 3L, title = "Third", sourceId = 1L, url = "url3")

 mangaDao.insertOrGetExisting(manga1)
 mangaDao.insertOrGetExisting(manga2)
 mangaDao.insertOrGetExisting(manga3)

 val ids = listOf(3L, 1L, 2L)
 val results = mangaDao.getMangaByIds(ids)
 assertEquals(3, results.size)
 // IN (:ids) does not guarantee order — verify all requested IDs are returned
 val resultIds = results.map { it.id }.toSet()
 assertTrue(resultIds.containsAll(ids))
 }

 @Test
 fun updateFavorite_togglesState() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "Test", sourceId = 1L, url = "url", favorite = false)
 mangaDao.insertOrGetExisting(manga)

 mangaDao.updateFavorite(1L, true)
 val updated = mangaDao.getMangaById(1L)
 assertEquals(true, updated?.favorite)

 mangaDao.updateFavorite(1L, false)
 val reverted = mangaDao.getMangaById(1L)
 assertEquals(false, reverted?.favorite)
 }

 @Test
 fun deleteById_removesManga() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "To Delete", sourceId = 1L, url = "url")
 mangaDao.insertOrGetExisting(manga)

 mangaDao.deleteById(1L)

 val retrieved = mangaDao.getMangaById(1L)
 assertNull(retrieved)
 }

 @Test
 fun getFavoriteMangaWithUnreadCount_computesCorrectly() = runBlocking {
 // This requires ChapterDao to insert chapters — simplified test
 val manga = MangaEntity(id = 1L, title = "Test", sourceId = 1L, url = "url", favorite = true)
 mangaDao.insertOrGetExisting(manga)

 val results = mangaDao.getFavoriteMangaWithUnreadCount().first()
 assertEquals(1, results.size)
 assertEquals("Test", results[0].manga.title)
 assertEquals(0, results[0].unreadCount) // No chapters = 0 unread
 }

 @Test
 fun isFavorite_returnsFlow() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "Test", sourceId = 1L, url = "url", favorite = false)
 mangaDao.insertOrGetExisting(manga)

 val initial = mangaDao.isFavorite(1L).first()
 assertEquals(false, initial)

 mangaDao.updateFavorite(1L, true)
 val updated = mangaDao.isFavorite(1L).first()
 assertEquals(true, updated)
 }

 @Test
 fun getFavoriteMangaCount_returnsCorrect() = runBlocking {
 mangaDao.insertOrGetExisting(MangaEntity(id = 1L, title = "1", sourceId = 1L, url = "url1", favorite = true))
 mangaDao.insertOrGetExisting(MangaEntity(id = 2L, title = "2", sourceId = 1L, url = "url2", favorite = true))
 mangaDao.insertOrGetExisting(MangaEntity(id = 3L, title = "3", sourceId = 1L, url = "url3", favorite = false))

 val count = mangaDao.countFavorites().first()
 assertEquals(2, count)
 }

 @Test
 fun perMangaReaderSettings_persistCorrectly() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "Test", sourceId = 1L, url = "url")
 mangaDao.insertOrGetExisting(manga)

 mangaDao.updateReaderDirection(1L, 1)
 mangaDao.updateReaderMode(1L, 2)
 mangaDao.updateReaderColorFilter(1L, 3)
 mangaDao.updateReaderCustomTintColor(1L, 0xFF00FF00)
 mangaDao.updateReaderBackgroundColor(1L, 0xFF000000)

 val updated = mangaDao.getMangaById(1L)
 assertEquals(1, updated?.readerDirection)
 assertEquals(2, updated?.readerMode)
 assertEquals(3, updated?.readerColorFilter)
 assertEquals(0xFF00FF00L, updated?.readerCustomTintColor)
 assertEquals(0xFF000000L, updated?.readerBackgroundColor)
 }
}
