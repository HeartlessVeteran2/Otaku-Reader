package app.otakureader.domain.repository

import app.otakureader.domain.model.SfxTranslation
import kotlinx.coroutines.flow.Flow

/**
 * Cache for AI-generated SFX translations.
 *
 * Implementations are expected to be fast (in-memory or lightweight DB) because
 * translations are looked up on every page navigation in the reader.
 */
interface SfxTranslationRepository {

    /**
     * Retrieve cached translations for a specific page.
     *
     * @param chapterId Database chapter ID.
     * @param pageIndex Zero-based page index within the chapter.
     * @return Cached translations, or `null` if no cache entry exists.
     *   An empty list means the page was previously analyzed and contains no SFX.
     */
    suspend fun getTranslations(chapterId: Long, pageIndex: Int): List<SfxTranslation>?

    /**
     * Observe translations for a specific page as a [Flow].
     *
     * Emits the latest cached list whenever it changes.
     */
    fun observeTranslations(chapterId: Long, pageIndex: Int): Flow<List<SfxTranslation>>

    /**
     * Persist translations for a specific page, replacing any previously cached value.
     */
    suspend fun saveTranslations(chapterId: Long, pageIndex: Int, translations: List<SfxTranslation>)

    /**
     * Remove all cached translations for the given chapter.
     */
    suspend fun clearTranslations(chapterId: Long)
}
