package app.otakureader.data.repository

import app.otakureader.core.database.dao.ReadingListDao
import app.otakureader.core.database.entity.ReadingListEntity
import app.otakureader.core.database.entity.ReadingListItemEntity
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.ReadingList
import app.otakureader.domain.model.ReadingListItem
import app.otakureader.domain.model.ReadingListMangaItem
import app.otakureader.domain.repository.ReadingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingListRepositoryImpl @Inject constructor(
    private val readingListDao: ReadingListDao
) : ReadingListRepository {

    override fun getAllLists(): Flow<List<ReadingList>> {
        return readingListDao.getAllLists().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getListById(listId: Long): ReadingList? {
        return readingListDao.getListById(listId)?.toDomain()
    }

    override suspend fun createList(name: String, description: String?, color: Int?): Long {
        val entity = ReadingListEntity(
            name = name,
            description = description,
            color = color
        )
        return readingListDao.insertList(entity)
    }

    override suspend fun updateList(list: ReadingList) {
        readingListDao.updateList(list.toEntity())
    }

    override suspend fun deleteList(listId: Long) {
        readingListDao.deleteListById(listId)
    }

    override suspend fun addMangaToList(listId: Long, mangaId: Long, note: String?) {
        readingListDao.addMangaToList(
            ReadingListItemEntity(listId = listId, mangaId = mangaId, note = note)
        )
    }

    override suspend fun removeMangaFromList(listId: Long, mangaId: Long) {
        readingListDao.removeMangaFromList(listId, mangaId)
    }

    override suspend fun isMangaInList(listId: Long, mangaId: Long): Boolean {
        return readingListDao.isMangaInList(listId, mangaId)
    }

    override fun getListsForManga(mangaId: Long): Flow<List<ReadingListItem>> {
        return readingListDao.getListsForManga(mangaId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateItemNote(listId: Long, mangaId: Long, note: String?) {
        readingListDao.updateItemNote(listId, mangaId, note)
    }

    override suspend fun reorderItem(listId: Long, mangaId: Long, sortOrder: Int) {
        readingListDao.updateItemSortOrder(listId, mangaId, sortOrder)
    }

    override fun getItemCount(listId: Long): Flow<Int> {
        return readingListDao.getItemCount(listId)
    }

    /**
     * Get a list with all its manga, preserving junction metadata.
     *
     * We combine two flows:
     * 1. `getListWithManga` — returns the list + manga entities via @Relation,
     *    but loses note/sortOrder from the junction table.
     * 2. `getItemsForList` — returns the raw junction rows with metadata.
     *
     * We then zip them by mangaId so every returned item carries its note + sortOrder.
     */
    override fun getListWithManga(listId: Long): Flow<Pair<ReadingList, List<ReadingListMangaItem>>> {
        val listFlow = readingListDao.getListWithManga(listId)
        val itemsFlow = readingListDao.getItemsForList(listId)

        return combine(listFlow, itemsFlow) { listEntity, itemEntities ->
            val list = listEntity?.list?.toDomain() ?: ReadingList(id = 0, name = "")
            // Build a lookup map from mangaId -> domain model
            val mangaMap = listEntity?.manga?.associateBy({ it.id }, { it.toDomain() }) ?: emptyMap()

            val mangaItems = itemEntities.mapNotNull { item ->
                val manga = mangaMap[item.mangaId] ?: return@mapNotNull null
                ReadingListMangaItem(
                    manga = manga,
                    note = item.note,
                    sortOrder = item.sortOrder,
                    addedAt = item.addedAt
                )
            }
            list to mangaItems
        }
    }

    private fun ReadingListEntity.toDomain(itemCount: Int = 0) = ReadingList(
        id = id,
        name = name,
        description = description,
        color = color,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sortOrder = sortOrder,
        itemCount = itemCount
    )

    private fun ReadingList.toEntity() = ReadingListEntity(
        id = id,
        name = name,
        description = description,
        color = color,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sortOrder = sortOrder
    )

    private fun ReadingListItemEntity.toDomain() = ReadingListItem(
        listId = listId,
        mangaId = mangaId,
        sortOrder = sortOrder,
        addedAt = addedAt,
        note = note
    )

    private fun app.otakureader.core.database.entity.MangaEntity.toDomain() = Manga(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        author = author,
        artist = artist,
        description = description,
        genre = genre?.split("|||")?.filter { it.isNotBlank() } ?: emptyList(),
        status = MangaStatus.fromOrdinal(status),
        favorite = favorite,
        initialized = initialized,
        autoDownload = autoDownload,
        dateAdded = dateAdded,
        readerBackgroundColor = readerBackgroundColor,
        chapterFlags = chapterFlags,
    )
}
