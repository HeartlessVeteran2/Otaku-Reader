package app.otakureader.data.repository

import app.otakureader.domain.model.OcrTranslation
import app.otakureader.domain.repository.OcrTranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [OcrTranslationRepository].
 *
 * Mirrors [SfxTranslationRepositoryImpl] in shape and rationale: translation
 * results are small per-page, sessions visit a small set of pages, and the
 * underlying images are already cached by Coil. Persisting to the database
 * would require a schema migration with no user-visible benefit; if persistent
 * caching is added later it can replace this implementation transparently.
 *
 * Thread-safety is provided by [ConcurrentHashMap] and [MutableStateFlow].
 */
@Singleton
class OcrTranslationRepositoryImpl @Inject constructor() : OcrTranslationRepository {

    /** Key: (chapterId, pageIndex). Value: list of translations for that page. */
    private val cache = ConcurrentHashMap<Pair<Long, Int>, List<OcrTranslation>>()

    /** Hot flow that emits whenever the cache changes. Subscribers re-derive their slice. */
    private val cacheVersion = MutableStateFlow(0L)

    override suspend fun getTranslations(chapterId: Long, pageIndex: Int): List<OcrTranslation>? {
        return cache[chapterId to pageIndex]
    }

    override fun observeTranslations(chapterId: Long, pageIndex: Int): Flow<List<OcrTranslation>> {
        return cacheVersion
            .map { cache[chapterId to pageIndex] ?: emptyList() }
            .distinctUntilChanged()
    }

    override suspend fun saveTranslations(
        chapterId: Long,
        pageIndex: Int,
        translations: List<OcrTranslation>,
    ) {
        cache[chapterId to pageIndex] = translations
        cacheVersion.update { it + 1 }
    }

    override suspend fun clearTranslations(chapterId: Long) {
        cache.keys.removeAll { (cId, _) -> cId == chapterId }
        cacheVersion.update { it + 1 }
    }
}
