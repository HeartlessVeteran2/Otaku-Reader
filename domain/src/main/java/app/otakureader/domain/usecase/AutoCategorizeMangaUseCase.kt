package app.otakureader.domain.usecase

import app.otakureader.domain.model.CategorizationResult
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for automatically categorizing manga when added to library.
 * This handles both auto-applying categories and storing suggestions for later review.
 *
 * **Note:** The caller is responsible for checking whether auto-categorization is
 * enabled (e.g. via preferences) before invoking this use case.
 */
class AutoCategorizeMangaUseCase @Inject constructor(
    private val suggestCategories: SuggestCategoriesUseCase,
    private val categoryRepository: CategoryRepository
) {
    companion object {
        /** Minimum confidence threshold for auto-applying categories */
        const val AUTO_APPLY_THRESHOLD = 0.85f
        /** Minimum confidence threshold for including in suggestions */
        const val SUGGESTION_THRESHOLD = 0.60f
    }

    /**
     * Auto-categorize a manga when it's added to the library.
     *
     * @param manga The manga that was added to the library
     * @return Result containing the categorization result, or error if AI is unavailable
     */
    suspend operator fun invoke(manga: Manga): Result<CategorizationResult> {
        // Get category suggestions from AI
        val suggestionsResult = suggestCategories(manga)
        
        return suggestionsResult.map { suggestions ->
            val filteredSuggestions = suggestions.filter { it.confidenceScore >= SUGGESTION_THRESHOLD }
            
            // Determine if we should auto-apply based on confidence
            val highConfidenceSuggestions = filteredSuggestions.filter { 
                it.confidenceScore >= AUTO_APPLY_THRESHOLD 
            }
            
            val shouldAutoApply = highConfidenceSuggestions.size >= 2
            
            val appliedCategories = if (shouldAutoApply) {
                applyCategories(manga.id, highConfidenceSuggestions.map { it.categoryName })
            } else {
                emptyList()
            }

            CategorizationResult(
                mangaId = manga.id,
                suggestions = filteredSuggestions,
                appliedCategories = appliedCategories,
                wasAutoApplied = shouldAutoApply
            )
        }
    }

    /**
     * Apply suggested categories to a manga.
     *
     * @param mangaId The manga ID
     * @param categoryNames List of category names to apply
     * @return List of successfully applied category names
     */
    private suspend fun applyCategories(mangaId: Long, categoryNames: List<String>): List<String> {
        val applied = mutableListOf<String>()
        
        categoryNames.forEach { categoryName ->
            try {
                // Find or create the category
                val existingCategories = categoryRepository.getCategories().first()
                val existingCategory = existingCategories.find { 
                    it.name.equals(categoryName, ignoreCase = true) 
                }
                
                val categoryId = if (existingCategory != null) {
                    existingCategory.id
                } else {
                    categoryRepository.createCategory(categoryName)
                }
                
                // Add manga to category
                categoryRepository.addMangaToCategory(mangaId, categoryId)
                applied.add(categoryName)
            } catch (_: Exception) {
                // Continue silently — failing to apply one category must not
                // prevent the remaining categories from being applied.
            }
        }
        
        return applied
    }
}
