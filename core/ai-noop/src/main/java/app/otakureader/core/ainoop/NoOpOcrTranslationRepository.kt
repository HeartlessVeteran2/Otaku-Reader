package app.otakureader.core.ainoop

import app.otakureader.domain.model.OcrTranslation
import app.otakureader.domain.repository.OcrTranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpOcrTranslationRepository @Inject constructor() : OcrTranslationRepository {

    override suspend fun getTranslations(chapterId: Long, pageIndex: Int): List<OcrTranslation>? = null

    override fun observeTranslations(chapterId: Long, pageIndex: Int): Flow<List<OcrTranslation>> = flowOf(emptyList())

    override suspend fun saveTranslations(chapterId: Long, pageIndex: Int, translations: List<OcrTranslation>) { /* no-op */ }

    override suspend fun clearTranslations(chapterId: Long) { /* no-op */ }
}
