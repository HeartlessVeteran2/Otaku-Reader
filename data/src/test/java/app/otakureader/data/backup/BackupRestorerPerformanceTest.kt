package app.otakureader.data.backup

import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaCategoryDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReaderPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext

class BackupRestorerPerformanceTest {

    private lateinit var mangaDao: MangaDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var mangaCategoryDao: MangaCategoryDao
    private lateinit var readingHistoryDao: ReadingHistoryDao
    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var readerPreferences: ReaderPreferences

    private lateinit var backupRestorer: BackupRestorer

    @Before
    fun setup() {
        mangaDao = mockk(relaxed = true)
        chapterDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        mangaCategoryDao = mockk(relaxed = true)
        readingHistoryDao = mockk(relaxed = true)
        generalPreferences = mockk(relaxed = true)
        libraryPreferences = mockk(relaxed = true)
        readerPreferences = mockk(relaxed = true)

        backupRestorer = BackupRestorer(
            mangaDao = mangaDao,
            chapterDao = chapterDao,
            categoryDao = categoryDao,
            mangaCategoryDao = mangaCategoryDao,
            readingHistoryDao = readingHistoryDao,
            generalPreferences = generalPreferences,
            libraryPreferences = libraryPreferences,
            readerPreferences = readerPreferences
        )
    }

    @Test
    fun testRestoreMangaCategoriesPerformance() = runTest {
        val restoreMangaCategoriesMethod = BackupRestorer::class.java.getDeclaredMethod("restoreMangaCategories", Long::class.java, List::class.java, Continuation::class.java)
        restoreMangaCategoriesMethod.isAccessible = true

        val mangaId = 1L
        // Simulate a lot of categories
        val categoryIds = (1L..1000L).toList()

        val time = measureTimeMillis {
            restoreMangaCategoriesMethod.invoke(backupRestorer, mangaId, categoryIds, Continuation<Unit>(EmptyCoroutineContext) { })
        }

        println("Time to restore categories: $time ms")
    }
}
