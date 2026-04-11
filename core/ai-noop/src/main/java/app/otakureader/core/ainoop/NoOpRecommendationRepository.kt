package app.otakureader.core.ainoop

import app.otakureader.domain.model.MangaRecommendation
import app.otakureader.domain.model.RecommendationInput
import app.otakureader.domain.model.RecommendationResult
import app.otakureader.domain.model.UserReadingPattern
import app.otakureader.domain.repository.RecommendationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op implementation of [RecommendationRepository] used in FOSS builds that exclude
 * the Gemini AI SDK.
 *
 * All methods return graceful failures, indicating that AI recommendations are not
 * available in this build variant.
 */
@Singleton
class NoOpRecommendationRepository @Inject constructor() : RecommendationRepository {

    private val _flow = MutableStateFlow<RecommendationResult?>(null)

    override suspend fun getRecommendations(forceRefresh: Boolean): Result<RecommendationResult> =
        Result.failure(UnsupportedOperationException("AI recommendations are not available in this build."))

    override fun observeRecommendations(): Flow<RecommendationResult?> = _flow.asStateFlow()

    override suspend fun analyzeReadingPatterns(): Result<UserReadingPattern> =
        Result.failure(UnsupportedOperationException("AI recommendations are not available in this build."))

    override suspend fun generateRecommendations(input: RecommendationInput): Result<RecommendationResult> =
        Result.failure(UnsupportedOperationException("AI recommendations are not available in this build."))

    override suspend fun clearCache() { /* no-op */ }

    override suspend fun getLastRefreshTime(): Long? = null

    override suspend fun needsRefresh(): Boolean = false

    override suspend fun refreshIfNeeded(): Result<RecommendationResult> =
        Result.failure(UnsupportedOperationException("AI recommendations are not available in this build."))

    override suspend fun markRecommendationViewed(recommendationId: String) { /* no-op */ }

    override suspend fun markRecommendationActioned(recommendationId: String, mangaId: Long) { /* no-op */ }

    override suspend fun dismissRecommendation(recommendationId: String) { /* no-op */ }

    override suspend fun getSimilarManga(mangaId: Long, limit: Int): Result<List<MangaRecommendation>> =
        Result.failure(UnsupportedOperationException("AI recommendations are not available in this build."))

    override suspend fun getForYouRecommendations(limit: Int): Result<List<MangaRecommendation>> =
        Result.failure(UnsupportedOperationException("AI recommendations are not available in this build."))
}
