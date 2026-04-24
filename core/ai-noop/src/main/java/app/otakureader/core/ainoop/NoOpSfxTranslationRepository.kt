package app.otakureader.core.ainoop

import app.otakureader.domain.model.SfxTranslation
import app.otakureader.domain.repository.SfxTranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpSfxTranslationRepository @Inject constructor() : SfxTranslationRepository {

    override suspend fun getTranslations(chapterId: Long, pageIndex: Int): List<SfxTranslation>? = null

    override fun observeTranslations(chapterId: Long, pageIndex: Int): Flow<List<SfxTranslation>> = flowOf(emptyList())

    override suspend fun saveTranslations(chapterId: Long, pageIndex: Int, translations: List<SfxTranslation>) { /* no-op */ }

    override suspend fun clearTranslations(chapterId: Long) { /* no-op */ }
}
