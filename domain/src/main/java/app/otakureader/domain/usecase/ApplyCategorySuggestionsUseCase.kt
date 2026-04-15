package app.otakureader.domain.usecase

import app.otakureader.domain.model.Category
import app.otakureader.domain.model.CategorySuggestion
import app.otakureader.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for getting category suggestions with information about existing categories.
 */
class GetCategorySuggestionsWithStatusUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    /**
     * Get category suggestions combined with existing categories.
     * This helps the UI show which suggestions are new vs. already exist.
     *
     * @param suggestions The AI-generated category suggestions
     * @return Flow of pairs containing (suggestion, existing category or null)
     */
    operator fun invoke(
        suggestions: List<CategorySuggestion>
    ): Flow<List<Pair<CategorySuggestion, Category?>>> {
        return categoryRepository.getCategories().map { existingCategories ->
            suggestions.map { suggestion ->
                val existing = existingCategories.find { 
                    it.name.equals(suggestion.categoryName, ignoreCase = true) 
                }
                suggestion to existing
            }
        }
    }
}

/**
 * Use case for applying selected categories from suggestions.
 */
class ApplyCategorySuggestionsUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    data class ApplicationResult(
        val appliedCategories: List<String>,
        val createdCategories: List<String>,
        val errors: List<String>
    )

    /**
     * Apply selected category suggestions to a manga.
     *
     * @param mangaId The manga ID
     * @param selectedCategoryNames The category names selected by the user
     * @return Result of the application
     */
    suspend operator fun invoke(
        mangaId: Long,
        selectedCategoryNames: List<String>
    ): ApplicationResult {
        val applied = mutableListOf<String>()
        val created = mutableListOf<String>()
        val errors = mutableListOf<String>()

        selectedCategoryNames.forEach { categoryName ->
            try {
                val existingCategories = categoryRepository.getCategories()
                    .let { flow ->
                        var list: List<Category>? = null
                        flow.collect { list = it }
                        list ?: emptyList()
                    }
                
                val existingCategory = existingCategories.find { 
                    it.name.equals(categoryName, ignoreCase = true) 
                }
                
                val categoryId = if (existingCategory != null) {
                    existingCategory.id
                } else {
                    created.add(categoryName)
                    categoryRepository.createCategory(categoryName)
                }
                
                categoryRepository.addMangaToCategory(mangaId, categoryId)
                applied.add(categoryName)
            } catch (e: Exception) {
                errors.add("$categoryName: ${e.message}")
            }
        }

        return ApplicationResult(applied, created, errors)
    }
}
