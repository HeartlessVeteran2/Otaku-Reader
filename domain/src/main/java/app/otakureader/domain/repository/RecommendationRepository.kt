package app.otakureader.domain.repository

import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.Recommendation
import kotlinx.coroutines.flow.Flow

/**
 * The recommendation carousel's backing store: a derived cache rebuilt wholesale, never edited.
 *
 * There is deliberately no `dismissRecommendation` here. One existed and nothing called it — the
 * UI dismisses through `LibraryPreferences`, and that is the correct home rather than an accident:
 * a dismissal recorded by deleting the cache row would be undone by the next weekly rebuild, which
 * clears the table and recomputes it from scratch. The preference outlives the cache, which is
 * exactly what a dismissal has to do.
 */
interface RecommendationRepository {
    fun getRecommendations(): Flow<List<Recommendation>>
    suspend fun refreshRecommendations(libraryManga: List<Manga>)
}
