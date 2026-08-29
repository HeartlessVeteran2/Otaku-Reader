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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {

 private lateinit var database: OtakuReaderDatabase
 private lateinit var categoryDao: CategoryDao
 private lateinit var mangaDao: MangaDao
 private lateinit var mangaCategoryDao: MangaCategoryDao

 @Before
 fun setup() {
 database = Room.inMemoryDatabaseBuilder(
 ApplicationProvider.getApplicationContext(),
 OtakuReaderDatabase::class.java
 ).allowMainThreadQueries().build()
 categoryDao = database.categoryDao()
 mangaDao = database.mangaDao()
 mangaCategoryDao = database.mangaCategoryDao()
 }

 @After
 fun teardown() {
 database.close()
 }

 @Test
 fun insertAndGetCategory() = runBlocking {
 val category = CategoryEntity(id = 1L, name = "Reading", order = 0)
 categoryDao.insert(category)

 val retrieved = categoryDao.getCategoryById(1L)
 assertNotNull(retrieved)
 assertEquals("Reading", retrieved?.name)
 assertEquals(0, retrieved?.order)
 }

 @Test
 fun getAllCategories_returnsInserted() = runBlocking {
 val categories = listOf(
 CategoryEntity(name = "Reading", order = 0),
 CategoryEntity(name = "Completed", order = 1),
 CategoryEntity(name = "Plan to Read", order = 2)
 )
 categories.forEach { categoryDao.insert(it) }

 val all = categoryDao.getCategories().first()
 assertEquals(3, all.size)
 assertEquals("Reading", all[0].name)
 assertEquals("Completed", all[1].name)
 }

 @Test
 fun deleteCategory_removesFromDatabase() = runBlocking {
 val category = CategoryEntity(id = 1L, name = "Reading", order = 0)
 categoryDao.insert(category)

 categoryDao.delete(category)

 val retrieved = categoryDao.getCategoryById(1L)
 assertNull(retrieved)
 }

 @Test
 fun updateCategory_changesName() = runBlocking {
 val category = CategoryEntity(id = 1L, name = "Reading", order = 0)
 categoryDao.insert(category)

 categoryDao.update(category.copy(name = "Currently Reading"))

 val retrieved = categoryDao.getCategoryById(1L)
 assertEquals("Currently Reading", retrieved?.name)
 }

 @Test
 fun getCategoryIdsForManga_returnsOnlyLinked() = runBlocking {
 // Insert manga
 val manga = MangaEntity(id = 1L, title = "Test Manga", sourceId = 1L, url = "url", favorite = true)
 mangaDao.insertOrGetExisting(manga)

 // Insert categories
 val cat1 = CategoryEntity(id = 1L, name = "Reading", order = 0)
 val cat2 = CategoryEntity(id = 2L, name = "Completed", order = 1)
 categoryDao.insert(cat1)
 categoryDao.insert(cat2)

 // Link manga to only first category
 categoryDao.insertMangaCategory(MangaCategoryEntity(mangaId = 1L, categoryId = 1L))

 val mangaCategoryIds = categoryDao.getCategoryIdsForManga(1L).first()
 assertEquals(1, mangaCategoryIds.size)
 assertEquals(1L, mangaCategoryIds[0])
 }

 @Test
 fun getDefaultCategory_returnsFirst_whenNoneSpecified() = runBlocking {
 val cat1 = CategoryEntity(id = 1L, name = "Reading", order = 0)
 categoryDao.insert(cat1)

 val default = categoryDao.getCategories().first().firstOrNull()
 assertNotNull(default)
 assertEquals("Reading", default?.name)
 }

 @Test
 fun reorderCategories_updatesOrder() = runBlocking {
 val cat1 = CategoryEntity(id = 1L, name = "Reading", order = 0)
 val cat2 = CategoryEntity(id = 2L, name = "Completed", order = 1)
 categoryDao.insert(cat1)
 categoryDao.insert(cat2)

 // Swap order
 categoryDao.update(cat1.copy(order = 1))
 categoryDao.update(cat2.copy(order = 0))

 val all = categoryDao.getCategories().first()
 assertEquals("Completed", all[0].name)
 assertEquals("Reading", all[1].name)
 }
}
