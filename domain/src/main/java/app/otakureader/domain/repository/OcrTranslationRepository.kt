package app.otakureader.domain.repository

import app.otakureader.domain.model.OcrTranslation
import kotlinx.coroutines.flow.Flow

/**
 * Cache for AI-generated OCR translations.
 *
 * Implementations are expected to be fast (in-memory or lightweight DB) because
 * translations are looked up on every page navigation in the reader.
 *
 * Mirrors [SfxTranslationRepository] in shape; the two are kept separate because
 * SFX and full-page text translations are independent features with different
 * lifecycles, prompts, and user toggles.
 */
interface OcrTranslationRepository {

    /**
     * Retrieve cached translations for a specific page.
     *
     * @param chapterId Database chapter ID.
     * @param pageIndex Zero-based page index within the chapter.
     * @return Cached translations, or `null` if no cache entry exists.
     *   An empty list means the page was previously analyzed and contains no text.
     */
    suspend fun getTranslations(chapterId: Long, pageIndex: Int): List<OcrTranslation>?

    /**
     * Observe translations for a specific page as a [Flow].
     *
     * Emits the latest cached list whenever it changes.
     */
    fun observeTranslations(chapterId: Long, pageIndex: Int): Flow<List<OcrTranslation>>

    /**
     * Persist translations for a specific page, replacing any previously cached value.
     */
    suspend fun saveTranslations(chapterId: Long, pageIndex: Int, translations: List<OcrTranslation>)

    /**
     * Remove all cached translations for the given chapter.
     */
    suspend fun clearTranslations(chapterId: Long)
}
