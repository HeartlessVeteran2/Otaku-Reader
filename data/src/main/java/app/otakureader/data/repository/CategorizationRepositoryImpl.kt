package app.otakureader.data.repository

import app.otakureader.core.database.dao.CategorizationResultDao
import app.otakureader.core.database.entity.CategorizationResultEntity
import app.otakureader.domain.model.CategorizationResult
import app.otakureader.domain.model.CategorySuggestion
import app.otakureader.domain.model.CategoryType
import app.otakureader.domain.repository.CategorizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [CategorizationRepository] using Room database.
 * Handles JSON serialization/deserialization of categorization results.
 */
@Singleton
class CategorizationRepositoryImpl @Inject constructor(
    private val dao: CategorizationResultDao
) : CategorizationRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun saveCategorizationResult(result: CategorizationResult) {
        val entity = result.toEntity()
        dao.insert(entity)
    }

    override suspend fun getCategorizationResult(mangaId: Long): CategorizationResult? {
        return dao.getByMangaId(mangaId)?.toDomain()
    }

    override fun getCategorizationResultFlow(mangaId: Long): Flow<CategorizationResult?> {
        return dao.getByMangaIdFlow(mangaId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun deleteCategorizationResult(mangaId: Long) {
        dao.deleteByMangaId(mangaId)
    }

    override fun getPendingSuggestions(): Flow<List<CategorizationResult>> {
        return dao.getPendingSuggestions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun markSuggestionsAsReviewed(mangaId: Long) {
        dao.markAsReviewed(mangaId)
    }

    /**
     * Convert domain model to entity.
     */
    private fun CategorizationResult.toEntity(): CategorizationResultEntity {
        val suggestionsJson = json.encodeToString(
            suggestions.map { it.toSerializable() }
        )
        val appliedCategoriesJson = json.encodeToString(appliedCategories)

        return CategorizationResultEntity(
            mangaId = mangaId,
            suggestionsJson = suggestionsJson,
            appliedCategoriesJson = appliedCategoriesJson,
            wasAutoApplied = wasAutoApplied,
            wasReviewed = false,
            timestamp = timestamp
        )
    }

    /**
     * Convert entity to domain model.
     */
    private fun CategorizationResultEntity.toDomain(): CategorizationResult {
        val suggestions = try {
            val serializableList = json.decodeFromString<List<SerializableCategorySuggestion>>(suggestionsJson)
            serializableList.map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }

        val appliedCategories = try {
            json.decodeFromString<List<String>>(appliedCategoriesJson)
        } catch (e: Exception) {
            emptyList()
        }

        return CategorizationResult(
            mangaId = mangaId,
            suggestions = suggestions,
            appliedCategories = appliedCategories,
            wasAutoApplied = wasAutoApplied,
            timestamp = timestamp
        )
    }

    /**
     * Convert CategorySuggestion to serializable format.
     */
    private fun CategorySuggestion.toSerializable() = SerializableCategorySuggestion(
        categoryName = categoryName,
        confidenceScore = confidenceScore,
        categoryType = categoryType.name
    )

    /**
     * Serializable representation of CategorySuggestion for JSON storage.
     */
    @Serializable
    private data class SerializableCategorySuggestion(
        val categoryName: String,
        val confidenceScore: Float,
        val categoryType: String
    ) {
        fun toDomain() = CategorySuggestion(
            categoryName = categoryName,
            confidenceScore = confidenceScore,
            categoryType = CategoryType.valueOf(categoryType)
        )
    }
}
