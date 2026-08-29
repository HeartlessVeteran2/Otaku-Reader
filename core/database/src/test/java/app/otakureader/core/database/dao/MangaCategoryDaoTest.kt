package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaCategoryDaoTest {

 private lateinit var database: OtakuReaderDatabase
 private lateinit var mangaCategoryDao: MangaCategoryDao
 private lateinit var mangaDao: MangaDao
 private lateinit var categoryDao: CategoryDao

 @Before
 fun setup() {
 database = Room.inMemoryDatabaseBuilder(
 ApplicationProvider.getApplicationContext(),
 OtakuReaderDatabase::class.java
 ).allowMainThreadQueries().build()
 mangaCategoryDao = database.mangaCategoryDao()
 mangaDao = database.mangaDao()
 categoryDao = database.categoryDao()
 }

 @After
 fun teardown() {
 database.close()
 }

 @Test
 fun insertMangaCategory_createsLink() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "Test", sourceId = 1L, url = "url", favorite = true)
 val category = CategoryEntity(id = 1L, name = "Reading", order = 0)
 mangaDao.insertOrGetExisting(manga)
 categoryDao.insert(category)

 mangaCategoryDao.upsert(MangaCategoryEntity(mangaId = 1L, categoryId = 1L))

 val categoryIds = categoryDao.getCategoryIdsForManga(1L).first()
 assertEquals(1, categoryIds.size)
 assertEquals(1L, categoryIds[0])
 }

 @Test
 fun deleteMangaCategory_removesLink() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "Test", sourceId = 1L, url = "url", favorite = true)
 val category = CategoryEntity(id = 1L, name = "Reading", order = 0)
 mangaDao.insertOrGetExisting(manga)
 categoryDao.insert(category)

 val link = MangaCategoryEntity(mangaId = 1L, categoryId = 1L)
 mangaCategoryDao.upsert(link)
 mangaCategoryDao.delete(1L, 1L)

 val categoryIds = categoryDao.getCategoryIdsForManga(1L).first()
 assertTrue(categoryIds.isEmpty())
 }

 @Test
 fun getMangaIdsForCategory_returnsOnlyLinked() = runBlocking {
 // Insert 2 manga
 val manga1 = MangaEntity(id = 1L, title = "Manga 1", sourceId = 1L, url = "url1", favorite = true)
 val manga2 = MangaEntity(id = 2L, title = "Manga 2", sourceId = 1L, url = "url2", favorite = true)
 mangaDao.insertOrGetExisting(manga1)
 mangaDao.insertOrGetExisting(manga2)

 // Insert 2 categories
 val cat1 = CategoryEntity(id = 1L, name = "Reading", order = 0)
 val cat2 = CategoryEntity(id = 2L, name = "Completed", order = 1)
 categoryDao.insert(cat1)
 categoryDao.insert(cat2)

 // Link: manga1->cat1, manga2->cat2
 mangaCategoryDao.upsert(MangaCategoryEntity(mangaId = 1L, categoryId = 1L))
 mangaCategoryDao.upsert(MangaCategoryEntity(mangaId = 2L, categoryId = 2L))

 val readingMangaIds = categoryDao.getMangaIdsByCategoryId(1L).first()
 assertEquals(1, readingMangaIds.size)
 assertEquals(1L, readingMangaIds[0])

 val completedMangaIds = categoryDao.getMangaIdsByCategoryId(2L).first()
 assertEquals(1, completedMangaIds.size)
 assertEquals(2L, completedMangaIds[0])
 }

 @Test
 fun deleteManga_deletesLinksViaCascade() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "Test", sourceId = 1L, url = "url", favorite = true)
 val category = CategoryEntity(id = 1L, name = "Reading", order = 0)
 mangaDao.insertOrGetExisting(manga)
 categoryDao.insert(category)
 mangaCategoryDao.upsert(MangaCategoryEntity(mangaId = 1L, categoryId = 1L))

 // Verify link exists
 val linksBefore = categoryDao.getCategoryIdsForManga(1L).first()
 assertEquals(1, linksBefore.size)

 // Delete manga (should cascade)
 mangaDao.deleteById(1L)

 // Link should be gone
 val linksAfter = categoryDao.getCategoryIdsForManga(1L).first()
 assertTrue(linksAfter.isEmpty())
 }

 @Test
 fun insertDuplicateLink_ignoresConflict() = runBlocking {
 val manga = MangaEntity(id = 1L, title = "Test", sourceId = 1L, url = "url", favorite = true)
 val category = CategoryEntity(id = 1L, name = "Reading", order = 0)
 mangaDao.insertOrGetExisting(manga)
 categoryDao.insert(category)

 val link = MangaCategoryEntity(mangaId = 1L, categoryId = 1L)
 mangaCategoryDao.upsert(link)
 mangaCategoryDao.upsert(link) // Duplicate

 val categoryIds = categoryDao.getCategoryIdsForManga(1L).first()
 assertEquals(1, categoryIds.size)
 }
}
