package app.otakureader.data.repository

import app.otakureader.core.database.dao.UpdateErrorDao
import app.otakureader.core.database.entity.UpdateErrorEntity
import app.otakureader.core.database.entity.UpdateErrorWithMangaEntity
import app.otakureader.domain.model.UpdateError
import app.otakureader.domain.repository.UpdateErrorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateErrorRepositoryImpl @Inject constructor(
    private val dao: UpdateErrorDao,
) : UpdateErrorRepository {

    override fun observeErrors(): Flow<List<UpdateError>> =
        dao.observeErrors().map { entities -> entities.map { it.toDomain() } }

    override suspend fun recordError(mangaId: Long, errorMessage: String) {
        dao.upsert(
            UpdateErrorEntity(
                mangaId = mangaId,
                errorMessage = errorMessage,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun clearError(mangaId: Long) {
        dao.deleteByMangaId(mangaId)
    }

    override suspend fun clearAllErrors() {
        dao.deleteAll()
    }

    private fun UpdateErrorWithMangaEntity.toDomain() = UpdateError(
        mangaId = error.mangaId,
        mangaTitle = manga.title,
        thumbnailUrl = manga.thumbnailUrl,
        errorMessage = error.errorMessage,
        timestamp = error.timestamp,
    )
}
