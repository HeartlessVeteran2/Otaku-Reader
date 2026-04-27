package app.otakureader.data.sync

import android.os.Build
import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.preferences.SyncPreferences
import app.otakureader.domain.model.SyncCategory
import app.otakureader.domain.model.SyncChapter
import app.otakureader.domain.model.SyncManga
import app.otakureader.domain.model.SyncResult
import app.otakureader.domain.model.SyncSnapshot
import app.otakureader.domain.sync.ConflictResolutionStrategy
import app.otakureader.domain.sync.SyncManager
import app.otakureader.domain.sync.SyncProvider
import app.otakureader.domain.sync.SyncStatus
import app.otakureader.core.common.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class SyncManagerImpl @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val syncPreferences: SyncPreferences,
    private val providers: Set<@JvmSuppressWildcards SyncProvider>,
    @ApplicationScope private val scope: CoroutineScope
) : SyncManager {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Disabled)

    override val syncStatus: Flow<SyncStatus> = _syncStatus.asStateFlow()
    override val isSyncEnabled: Flow<Boolean> = syncPreferences.isSyncEnabled

    init {
        scope.launch {
            syncPreferences.isSyncEnabled.collect { enabled ->
                _syncStatus.update { current ->
                    when {
                        !enabled -> SyncStatus.Disabled
                        current is SyncStatus.Syncing -> current
                        else -> SyncStatus.Idle
                    }
                }
            }
        }
    }

    override suspend fun enableSync(providerId: String): Result<Unit> = runCatching {
        val provider = providers.firstOrNull { it.id == providerId }
            ?: error("Unknown sync provider: $providerId")
        syncPreferences.setProvider(provider.id)
        syncPreferences.setSyncEnabled(true)
    }.onFailure { error ->
        _syncStatus.value = SyncStatus.Error(error.message ?: "Failed to enable sync", error)
    }

    override suspend fun disableSync(clearMetadata: Boolean) {
        syncPreferences.setSyncEnabled(false)
        if (clearMetadata) {
            syncPreferences.clearMetadata()
        }
    }

    override suspend fun sync(): Result<SyncResult> = runCatching {
        val provider = requireActiveProvider()
        _syncStatus.value = SyncStatus.Syncing()

        val snapshot = createSnapshot()
        provider.uploadSnapshot(snapshot).getOrThrow()

        val remoteSnapshot = provider.downloadSnapshot().getOrThrow()
        val result = if (remoteSnapshot != null) {
            applySnapshot(remoteSnapshot, ConflictResolutionStrategy.MERGE).getOrThrow()
        } else {
            SyncResult(
                success = true,
                message = "No remote snapshot found; uploaded local state."
            )
        }

        syncPreferences.setLastSyncTime(System.currentTimeMillis())
        _syncStatus.value = SyncStatus.Success(result)
        result
    }.onFailure { error ->
        _syncStatus.value = SyncStatus.Error(error.message ?: "Sync failed", error)
    }

    override suspend fun pushToCloud(): Result<Unit> = runCatching {
        val provider = requireActiveProvider()
        _syncStatus.value = SyncStatus.Syncing()
        val snapshot = createSnapshot()
        provider.uploadSnapshot(snapshot).getOrThrow()
        syncPreferences.setLastSyncTime(System.currentTimeMillis())
        _syncStatus.value = SyncStatus.Success(
            SyncResult(
                success = true,
                message = "Uploaded local snapshot."
            )
        )
    }.onFailure { error ->
        _syncStatus.value = SyncStatus.Error(error.message ?: "Push failed", error)
    }

    override suspend fun pullFromCloud(): Result<SyncResult> = runCatching {
        val provider = requireActiveProvider()
        _syncStatus.value = SyncStatus.Syncing()
        val remoteSnapshot = provider.downloadSnapshot().getOrThrow()
        val result = if (remoteSnapshot != null) {
            applySnapshot(remoteSnapshot, ConflictResolutionStrategy.MERGE).getOrThrow()
        } else {
            SyncResult(success = true, message = "No remote snapshot to apply")
        }
        syncPreferences.setLastSyncTime(System.currentTimeMillis())
        _syncStatus.value = SyncStatus.Success(result)
        result
    }.onFailure { error ->
        _syncStatus.value = SyncStatus.Error(error.message ?: "Pull failed", error)
    }

    override suspend fun getLastSyncTime(): Long? = syncPreferences.lastSyncTime.first()

    override suspend fun createSnapshot(): SyncSnapshot = withContext(Dispatchers.IO) {
        val deviceId = syncPreferences.getOrCreateDeviceId()
        val deviceName = Build.MODEL

        val categories = categoryDao.getCategories().first().map { category ->
            SyncCategory(
                id = category.id,
                name = category.name,
                order = category.order,
                lastModified = System.currentTimeMillis()
            )
        }

        val favoriteManga = mangaDao.getFavoriteManga().first()
        val manga = favoriteManga.map { entity ->
            val categoryIds = categoryDao.getCategoryIdsForManga(entity.id).first()
            val chapters = chapterDao.getChaptersByMangaId(entity.id).first().map { chapter ->
                SyncChapter(
                    url = chapter.url,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    lastModified = chapter.lastModified.takeIf { it > 0 } ?: chapter.dateFetch
                )
            }

            SyncManga(
                sourceId = entity.sourceId,
                url = entity.url,
                title = entity.title,
                thumbnailUrl = entity.thumbnailUrl,
                favorite = entity.favorite,
                categoryIds = categoryIds,
                lastModified = computeMangaLastModified(entity),
                notes = entity.notes,
                chapters = chapters
            )
        }

        SyncSnapshot(
            deviceId = deviceId,
            deviceName = deviceName,
            manga = manga,
            categories = categories
        )
    }

    override suspend fun applySnapshot(
        snapshot: SyncSnapshot,
        strategy: ConflictResolutionStrategy
    ): Result<SyncResult> = runCatching {
        withContext(Dispatchers.IO) {
            var mangaAdded = 0
            var mangaUpdated = 0
            var categoriesAdded = 0
            var categoriesUpdated = 0
            var chaptersUpdated = 0

            // Categories first so relationships are valid.
            snapshot.categories.forEach { remoteCategory ->
                val local = categoryDao.getCategoryById(remoteCategory.id)
                if (local == null) {
                    categoryDao.insert(
                        CategoryEntity(
                            id = remoteCategory.id,
                            name = remoteCategory.name,
                            order = remoteCategory.order
                        )
                    )
                    categoriesAdded++
                } else {
                    val shouldUpdate = when (strategy) {
                        ConflictResolutionStrategy.PREFER_LOCAL -> false
                        ConflictResolutionStrategy.PREFER_REMOTE -> true
                        ConflictResolutionStrategy.PREFER_NEWER -> true
                        ConflictResolutionStrategy.MERGE -> remoteCategory.lastModified >= 0
                    }

                    val needsChange = local.name != remoteCategory.name || local.order != remoteCategory.order
                    if (shouldUpdate && needsChange) {
                        categoryDao.update(
                            local.copy(
                                name = remoteCategory.name,
                                order = remoteCategory.order
                            )
                        )
                        categoriesUpdated++
                    }
                }
            }

            snapshot.manga.forEach { remoteManga ->
                val local = mangaDao.getMangaBySourceAndUrl(remoteManga.sourceId, remoteManga.url)
                if (local == null) {
                    val insertedId = mangaDao.insert(
                        MangaEntity(
                            sourceId = remoteManga.sourceId,
                            url = remoteManga.url,
                            title = remoteManga.title,
                            thumbnailUrl = remoteManga.thumbnailUrl,
                            favorite = remoteManga.favorite,
                            lastUpdate = remoteManga.lastModified,
                            dateAdded = System.currentTimeMillis(),
                            notes = remoteManga.notes
                        )
                    )
                    updateMangaCategories(insertedId, remoteManga.categoryIds)
                    chaptersUpdated += applyChapters(
                        mangaId = insertedId,
                        remoteChapters = remoteManga.chapters,
                        strategy = strategy
                    )
                    mangaAdded++
                } else {
                    val merged = mergeManga(local, remoteManga, strategy)
                    if (merged != local) {
                        mangaDao.update(merged)
                        mangaUpdated++
                    }

                    val mergedCategoryIds = mergeCategoryIds(
                        localIds = categoryDao.getCategoryIdsForManga(local.id).first(),
                        remoteIds = remoteManga.categoryIds,
                        strategy = strategy,
                        localTimestamp = computeMangaLastModified(local),
                        remoteTimestamp = remoteManga.lastModified
                    )
                    updateMangaCategories(local.id, mergedCategoryIds)

                    chaptersUpdated += applyChapters(
                        mangaId = local.id,
                        remoteChapters = remoteManga.chapters,
                        strategy = strategy
                    )
                }
            }

            SyncResult(
                success = true,
                mangaAdded = mangaAdded,
                mangaUpdated = mangaUpdated,
                chaptersUpdated = chaptersUpdated,
                categoriesAdded = categoriesAdded,
                categoriesUpdated = categoriesUpdated,
                message = "Applied snapshot from ${snapshot.deviceName ?: "remote device"}"
            )
        }
    }

    private suspend fun applyChapters(
        mangaId: Long,
        remoteChapters: List<SyncChapter>,
        strategy: ConflictResolutionStrategy
    ): Int {
        var updatedCount = 0

        remoteChapters.forEach { remoteChapter ->
            val local = chapterDao.getChapterByMangaIdAndUrl(mangaId, remoteChapter.url)
            if (local == null) {
                chapterDao.insert(
                    ChapterEntity(
                        mangaId = mangaId,
                        url = remoteChapter.url,
                        name = remoteChapter.url,
                        read = remoteChapter.read,
                        bookmark = remoteChapter.bookmark,
                        lastPageRead = remoteChapter.lastPageRead,
                        lastModified = remoteChapter.lastModified
                    )
                )
                updatedCount++
            } else {
                val updated = mergeChapter(local, remoteChapter, strategy)
                if (updated != local) {
                    chapterDao.update(updated)
                    updatedCount++
                }
            }
        }

        return updatedCount
    }

    private fun mergeChapter(
        local: ChapterEntity,
        remote: SyncChapter,
        strategy: ConflictResolutionStrategy
    ): ChapterEntity {
        val localTimestamp = local.lastModified.takeIf { it > 0 } ?: local.dateFetch
        val preferRemote = when (strategy) {
            ConflictResolutionStrategy.PREFER_REMOTE -> true
            ConflictResolutionStrategy.PREFER_LOCAL -> false
            ConflictResolutionStrategy.PREFER_NEWER -> remote.lastModified >= localTimestamp
            ConflictResolutionStrategy.MERGE -> false
        }

        val read = when (strategy) {
            ConflictResolutionStrategy.MERGE -> local.read || remote.read
            else -> if (preferRemote) remote.read else local.read
        }

        val bookmark = when (strategy) {
            ConflictResolutionStrategy.MERGE -> local.bookmark || remote.bookmark
            else -> if (preferRemote) remote.bookmark else local.bookmark
        }

        val lastPageRead = when (strategy) {
            ConflictResolutionStrategy.MERGE -> max(local.lastPageRead, remote.lastPageRead)
            else -> if (preferRemote) remote.lastPageRead else local.lastPageRead
        }

        val lastModified = when (strategy) {
            ConflictResolutionStrategy.MERGE -> max(localTimestamp, remote.lastModified)
            else -> if (preferRemote) remote.lastModified else localTimestamp
        }

        return local.copy(
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            lastModified = lastModified
        )
    }

    private fun mergeManga(
        local: MangaEntity,
        remote: SyncManga,
        strategy: ConflictResolutionStrategy
    ): MangaEntity {
        val localTimestamp = computeMangaLastModified(local)
        val preferRemote = when (strategy) {
            ConflictResolutionStrategy.PREFER_REMOTE -> true
            ConflictResolutionStrategy.PREFER_LOCAL -> false
            ConflictResolutionStrategy.PREFER_NEWER -> remote.lastModified >= localTimestamp
            ConflictResolutionStrategy.MERGE -> false
        }

        val favorite = when (strategy) {
            ConflictResolutionStrategy.MERGE -> local.favorite || remote.favorite
            else -> if (preferRemote) remote.favorite else local.favorite
        }

        val notes = when (strategy) {
            ConflictResolutionStrategy.MERGE -> remote.notes ?: local.notes
            else -> if (preferRemote) remote.notes else local.notes
        }

        val lastUpdate = when (strategy) {
            ConflictResolutionStrategy.MERGE -> max(localTimestamp, remote.lastModified)
            else -> if (preferRemote) remote.lastModified else localTimestamp
        }

        val thumbnailUrl = if (preferRemote) remote.thumbnailUrl ?: local.thumbnailUrl else local.thumbnailUrl

        return local.copy(
            favorite = favorite,
            notes = notes,
            thumbnailUrl = thumbnailUrl,
            lastUpdate = lastUpdate
        )
    }

    private suspend fun updateMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        categoryDao.deleteMangaCategoriesForManga(mangaId)
        categoryIds.distinct().forEach { categoryId ->
            categoryDao.insertMangaCategory(
                MangaCategoryEntity(
                    mangaId = mangaId,
                    categoryId = categoryId
                )
            )
        }
    }

    private fun mergeCategoryIds(
        localIds: List<Long>,
        remoteIds: List<Long>,
        strategy: ConflictResolutionStrategy,
        localTimestamp: Long,
        remoteTimestamp: Long
    ): List<Long> {
        return when (strategy) {
            ConflictResolutionStrategy.PREFER_LOCAL -> localIds
            ConflictResolutionStrategy.PREFER_REMOTE -> remoteIds
            ConflictResolutionStrategy.PREFER_NEWER ->
                if (remoteTimestamp >= localTimestamp) remoteIds else localIds
            ConflictResolutionStrategy.MERGE -> (localIds + remoteIds).distinct()
        }
    }

    private fun computeMangaLastModified(entity: MangaEntity): Long {
        return listOf(entity.lastUpdate, entity.coverLastModified, entity.dateAdded).maxOrNull()
            ?: System.currentTimeMillis()
    }

    private suspend fun requireActiveProvider(): SyncProvider {
        val providerId = syncPreferences.providerId.first()
        val provider = providers.firstOrNull { it.id == providerId }
        return provider ?: error("Sync provider not configured")
    }
}
