package app.otakureader.data.sync

import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.preferences.SyncPreferences
import app.otakureader.domain.model.SyncCategory
import app.otakureader.domain.model.SyncChapter
import app.otakureader.domain.model.SyncManga
import app.otakureader.domain.model.SyncSnapshot
import app.otakureader.domain.sync.ConflictResolutionStrategy
import app.otakureader.domain.sync.SyncProvider
import app.otakureader.domain.sync.SyncStatus
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SyncManagerImpl.
 */
class SyncManagerImplTest {

    private lateinit var syncManager: SyncManagerImpl
    private lateinit var mangaDao: MangaDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var syncPreferences: SyncPreferences
    private lateinit var mockProvider: SyncProvider

    @Before
    fun setup() {
        mangaDao = mockk(relaxed = true)
        chapterDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        syncPreferences = mockk(relaxed = true)
        mockProvider = mockk(relaxed = true)

        every { mockProvider.id } returns "test_provider"
        every { mockProvider.name } returns "Test Provider"

        every { syncPreferences.isSyncEnabled } returns flowOf(true)
        every { syncPreferences.providerId } returns flowOf("test_provider")
        coEvery { syncPreferences.getOrCreateDeviceId() } returns "test-device-123"

        syncManager = SyncManagerImpl(
            mangaDao = mangaDao,
            chapterDao = chapterDao,
            categoryDao = categoryDao,
            syncPreferences = syncPreferences,
            providers = setOf(mockProvider)
        )
    }

    @Test
    fun `enableSync sets provider and enables sync`() = runTest {
        // Given
        coEvery { syncPreferences.setProvider(any()) } just Runs
        coEvery { syncPreferences.setSyncEnabled(any()) } just Runs

        // When
        val result = syncManager.enableSync("test_provider")

        // Then
        assertTrue(result.isSuccess)
        coVerify {
            syncPreferences.setProvider("test_provider")
            syncPreferences.setSyncEnabled(true)
        }
    }

    @Test
    fun `enableSync fails for unknown provider`() = runTest {
        // When
        val result = syncManager.enableSync("unknown_provider")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unknown sync provider") == true)
    }

    @Test
    fun `disableSync disables sync and optionally clears metadata`() = runTest {
        // Given
        coEvery { syncPreferences.setSyncEnabled(any()) } just Runs
        coEvery { syncPreferences.clearMetadata() } just Runs

        // When
        syncManager.disableSync(clearMetadata = true)

        // Then
        coVerify {
            syncPreferences.setSyncEnabled(false)
            syncPreferences.clearMetadata()
        }
    }

    @Test
    fun `createSnapshot creates snapshot from database`() = runTest {
        // Given
        val category = CategoryEntity(id = 1L, name = "Action", order = 0)
        val manga = MangaEntity(
            id = 1L,
            sourceId = 100L,
            url = "/manga/test",
            title = "Test Manga",
            favorite = true,
            lastUpdate = 1234567890L,
            dateAdded = 1234567890L
        )
        val chapter = ChapterEntity(
            id = 1L,
            mangaId = 1L,
            url = "/chapter/1",
            name = "Chapter 1",
            read = true,
            bookmark = false,
            lastPageRead = 10,
            lastModified = 1234567890L,
            dateFetch = 1234567890L
        )

        every { categoryDao.getCategories() } returns flowOf(listOf(category))
        every { mangaDao.getFavoriteManga() } returns flowOf(listOf(manga))
        every { categoryDao.getCategoryIdsForManga(1L) } returns flowOf(listOf(1L))
        every { chapterDao.getChaptersByMangaId(1L) } returns flowOf(listOf(chapter))

        // When
        val snapshot = syncManager.createSnapshot()

        // Then
        assertNotNull(snapshot)
        assertEquals("test-device-123", snapshot.deviceId)
        assertEquals(1, snapshot.categories.size)
        assertEquals("Action", snapshot.categories[0].name)
        assertEquals(1, snapshot.manga.size)
        assertEquals("Test Manga", snapshot.manga[0].title)
        assertEquals(1, snapshot.manga[0].chapters.size)
        assertTrue(snapshot.manga[0].chapters[0].read)
    }

    @Test
    fun `applySnapshot adds new manga`() = runTest {
        // Given
        val snapshot = SyncSnapshot(
            deviceId = "remote-device",
            manga = listOf(
                SyncManga(
                    sourceId = 100L,
                    url = "/manga/new",
                    title = "New Manga",
                    favorite = true,
                    categoryIds = emptyList(),
                    lastModified = System.currentTimeMillis()
                )
            ),
            categories = emptyList()
        )

        coEvery { mangaDao.getMangaBySourceAndUrl(any(), any()) } returns null
        coEvery { mangaDao.insert(any()) } returns 1L
        coEvery { categoryDao.getCategoryIdsForManga(any()) } returns flowOf(emptyList())
        coEvery { categoryDao.deleteMangaCategoriesForManga(any()) } just Runs
        coEvery { chapterDao.getChapterByMangaIdAndUrl(any(), any()) } returns null

        // When
        val result = syncManager.applySnapshot(snapshot, ConflictResolutionStrategy.PREFER_NEWER)

        // Then
        assertTrue(result.isSuccess)
        val syncResult = result.getOrNull()
        assertNotNull(syncResult)
        assertEquals(1, syncResult?.mangaAdded)
        coVerify { mangaDao.insert(any()) }
    }

    @Test
    fun `applySnapshot merges existing manga with MERGE strategy`() = runTest {
        // Given
        val existingManga = MangaEntity(
            id = 1L,
            sourceId = 100L,
            url = "/manga/test",
            title = "Test Manga",
            favorite = false, // Not favorite locally
            lastUpdate = 1000L,
            dateAdded = 1000L
        )

        val snapshot = SyncSnapshot(
            deviceId = "remote-device",
            manga = listOf(
                SyncManga(
                    sourceId = 100L,
                    url = "/manga/test",
                    title = "Test Manga",
                    favorite = true, // Favorite remotely
                    categoryIds = emptyList(),
                    lastModified = 2000L
                )
            ),
            categories = emptyList()
        )

        coEvery { mangaDao.getMangaBySourceAndUrl(100L, "/manga/test") } returns existingManga
        coEvery { mangaDao.update(any()) } just Runs
        coEvery { categoryDao.getCategoryIdsForManga(1L) } returns flowOf(emptyList())
        coEvery { categoryDao.deleteMangaCategoriesForManga(any()) } just Runs
        coEvery { chapterDao.getChapterByMangaIdAndUrl(any(), any()) } returns null

        // When
        val result = syncManager.applySnapshot(snapshot, ConflictResolutionStrategy.MERGE)

        // Then
        assertTrue(result.isSuccess)
        val syncResult = result.getOrNull()
        assertNotNull(syncResult)
        assertEquals(1, syncResult?.mangaUpdated)

        // Verify manga was updated with merged favorite status (true OR false = true)
        coVerify {
            mangaDao.update(match { it.favorite == true })
        }
    }

    @Test
    fun `applySnapshot merges chapter progress with MERGE strategy`() = runTest {
        // Given
        val existingChapter = ChapterEntity(
            id = 1L,
            mangaId = 1L,
            url = "/chapter/1",
            name = "Chapter 1",
            read = false,
            bookmark = false,
            lastPageRead = 5,
            lastModified = 1000L,
            dateFetch = 1000L
        )

        val existingManga = MangaEntity(
            id = 1L,
            sourceId = 100L,
            url = "/manga/test",
            title = "Test Manga",
            favorite = true,
            lastUpdate = 1000L,
            dateAdded = 1000L
        )

        val snapshot = SyncSnapshot(
            deviceId = "remote-device",
            manga = listOf(
                SyncManga(
                    sourceId = 100L,
                    url = "/manga/test",
                    title = "Test Manga",
                    favorite = true,
                    chapters = listOf(
                        SyncChapter(
                            url = "/chapter/1",
                            read = true, // Read remotely
                            bookmark = true, // Bookmarked remotely
                            lastPageRead = 10, // Further progress remotely
                            lastModified = 2000L
                        )
                    ),
                    lastModified = 2000L
                )
            ),
            categories = emptyList()
        )

        coEvery { mangaDao.getMangaBySourceAndUrl(100L, "/manga/test") } returns existingManga
        coEvery { categoryDao.getCategoryIdsForManga(1L) } returns flowOf(emptyList())
        coEvery { categoryDao.deleteMangaCategoriesForManga(any()) } just Runs
        coEvery { chapterDao.getChapterByMangaIdAndUrl(1L, "/chapter/1") } returns existingChapter
        coEvery { chapterDao.update(any()) } just Runs

        // When
        val result = syncManager.applySnapshot(snapshot, ConflictResolutionStrategy.MERGE)

        // Then
        assertTrue(result.isSuccess)

        // Verify chapter was updated with merged values
        coVerify {
            chapterDao.update(match {
                it.read == true && // Merged: false OR true = true
                it.bookmark == true && // Merged: false OR true = true
                it.lastPageRead == 10 // Merged: max(5, 10) = 10
            })
        }
    }

    @Test
    fun `sync status transitions correctly`() = runTest {
        // Given
        every { syncPreferences.isSyncEnabled } returns flowOf(true)

        // When - observe initial status
        val status = syncManager.syncStatus.first { it is SyncStatus.Idle }

        // Then
        assertTrue(status is SyncStatus.Idle)
    }

    @Test
    fun `sync uploads and downloads snapshot`() = runTest {
        // Given
        val remoteSnapshot = SyncSnapshot(
            deviceId = "remote-device",
            manga = emptyList(),
            categories = emptyList()
        )

        every { categoryDao.getCategories() } returns flowOf(emptyList())
        every { mangaDao.getFavoriteManga() } returns flowOf(emptyList())
        coEvery { mockProvider.uploadSnapshot(any()) } returns Result.success(Unit)
        coEvery { mockProvider.downloadSnapshot() } returns Result.success(remoteSnapshot)
        coEvery { syncPreferences.setLastSyncTime(any()) } just Runs

        // When
        val result = syncManager.sync()

        // Then
        assertTrue(result.isSuccess)
        coVerify {
            mockProvider.uploadSnapshot(any())
            mockProvider.downloadSnapshot()
            syncPreferences.setLastSyncTime(any())
        }
    }

    @Test
    fun `getLastSyncTime returns timestamp from preferences`() = runTest {
        // Given
        every { syncPreferences.lastSyncTime } returns flowOf(123456789L)

        // When
        val time = syncManager.getLastSyncTime()

        // Then
        assertEquals(123456789L, time)
    }
}
