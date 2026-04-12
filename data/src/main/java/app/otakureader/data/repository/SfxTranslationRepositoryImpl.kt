package app.otakureader.data.repository

import app.otakureader.domain.model.SfxTranslation
import app.otakureader.domain.repository.SfxTranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [SfxTranslationRepository].
 *
 * Translations are generated on-demand and held for the lifetime of the process.
 * This is appropriate because:
 * - Individual translation results are small (a few strings per page).
 * - The set of pages viewed in any session is small.
 * - Persisting to the database would require a schema migration with no user-visible
 *   benefit (pages are already cached at the image level by Coil).
 *
 * Thread-safety is provided by [ConcurrentHashMap] and [MutableStateFlow].
 */
@Singleton
class SfxTranslationRepositoryImpl @Inject constructor() : SfxTranslationRepository {

    /** Key: (chapterId, pageIndex). Value: list of translations for that page. */
    private val cache = ConcurrentHashMap<Pair<Long, Int>, List<SfxTranslation>>()

    /** Hot flow that emits whenever the cache changes. Subscribers re-derive their slice. */
    private val cacheVersion = MutableStateFlow(0L)

    override suspend fun getTranslations(chapterId: Long, pageIndex: Int): List<SfxTranslation>? {
        return cache[chapterId to pageIndex]
    }

    override fun observeTranslations(chapterId: Long, pageIndex: Int): Flow<List<SfxTranslation>> {
        return cacheVersion.map { cache[chapterId to pageIndex] ?: emptyList() }
    }

    override suspend fun saveTranslations(
        chapterId: Long,
        pageIndex: Int,
        translations: List<SfxTranslation>,
    ) {
        cache[chapterId to pageIndex] = translations
        cacheVersion.update { it + 1 }
    }

    override suspend fun clearTranslations(chapterId: Long) {
        cache.keys.removeAll { (cId, _) -> cId == chapterId }
        cacheVersion.update { it + 1 }
    }
}
