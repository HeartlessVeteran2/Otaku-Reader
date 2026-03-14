package app.otakureader.domain.usecase.sync

import app.otakureader.domain.model.SyncResult
import app.otakureader.domain.sync.SyncManager
import app.otakureader.domain.sync.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for sync use cases.
 */
class SyncUseCasesTest {

    private lateinit var syncManager: SyncManager

    @Before
    fun setup() {
        syncManager = mockk(relaxed = true)
    }

    @Test
    fun `EnableSyncUseCase calls syncManager enableSync`() = runTest {
        // Given
        val useCase = EnableSyncUseCase(syncManager)
        coEvery { syncManager.enableSync(any()) } returns Result.success(Unit)

        // When
        val result = useCase("google_drive")

        // Then
        assertTrue(result.isSuccess)
        coVerify { syncManager.enableSync("google_drive") }
    }

    @Test
    fun `EnableSyncUseCase propagates error`() = runTest {
        // Given
        val useCase = EnableSyncUseCase(syncManager)
        val error = Exception("Provider not found")
        coEvery { syncManager.enableSync(any()) } returns Result.failure(error)

        // When
        val result = useCase("unknown")

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `DisableSyncUseCase calls syncManager disableSync`() = runTest {
        // Given
        val useCase = DisableSyncUseCase(syncManager)
        coEvery { syncManager.disableSync(any()) } returns Unit

        // When
        useCase(clearMetadata = true)

        // Then
        coVerify { syncManager.disableSync(clearMetadata = true) }
    }

    @Test
    fun `DisableSyncUseCase defaults to not clearing metadata`() = runTest {
        // Given
        val useCase = DisableSyncUseCase(syncManager)
        coEvery { syncManager.disableSync(any()) } returns Unit

        // When
        useCase()

        // Then
        coVerify { syncManager.disableSync(clearMetadata = false) }
    }

    @Test
    fun `SyncNowUseCase calls syncManager sync`() = runTest {
        // Given
        val useCase = SyncNowUseCase(syncManager)
        val syncResult = SyncResult(
            success = true,
            mangaAdded = 5,
            mangaUpdated = 3,
            chaptersUpdated = 10
        )
        coEvery { syncManager.sync() } returns Result.success(syncResult)

        // When
        val result = useCase()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(syncResult, result.getOrNull())
        coVerify { syncManager.sync() }
    }

    @Test
    fun `SyncNowUseCase propagates sync error`() = runTest {
        // Given
        val useCase = SyncNowUseCase(syncManager)
        val error = Exception("Sync failed")
        coEvery { syncManager.sync() } returns Result.failure(error)

        // When
        val result = useCase()

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `ObserveSyncStatusUseCase returns syncStatus flow`() = runTest {
        // Given
        val useCase = ObserveSyncStatusUseCase(syncManager)
        val status = SyncStatus.Idle
        every { syncManager.syncStatus } returns flowOf(status)

        // When
        val result = useCase()

        // Then
        assertNotNull(result)
        // Flow returns the status
        assertEquals(status, result.first())
    }

    @Test
    fun `GetLastSyncTimeUseCase returns timestamp`() = runTest {
        // Given
        val useCase = GetLastSyncTimeUseCase(syncManager)
        val timestamp = 123456789L
        coEvery { syncManager.getLastSyncTime() } returns timestamp

        // When
        val result = useCase()

        // Then
        assertEquals(timestamp, result)
        coVerify { syncManager.getLastSyncTime() }
    }

    @Test
    fun `GetLastSyncTimeUseCase returns null for no sync`() = runTest {
        // Given
        val useCase = GetLastSyncTimeUseCase(syncManager)
        coEvery { syncManager.getLastSyncTime() } returns null

        // When
        val result = useCase()

        // Then
        assertNull(result)
    }
}
