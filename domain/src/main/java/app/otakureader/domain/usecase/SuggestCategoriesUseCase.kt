package app.otakureader.domain.usecase

import app.otakureader.domain.model.CategorizationResult
import app.otakureader.domain.model.CategorySuggestion
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.StandardCategories
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for generating AI-powered category suggestions for a manga.
 */
class SuggestCategoriesUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    /**
     * Generate category suggestions for a manga based on its metadata.
     *
     * @param manga The manga to analyze
     * @return Result containing list of category suggestions, or error if AI is unavailable
     */
    suspend operator fun invoke(manga: Manga): Result<List<CategorySuggestion>> {
        if (!aiRepository.isAvailable()) {
            return Result.failure(IllegalStateException("AI service is not available"))
        }

        val prompt = buildCategorizationPrompt(manga)
        
        return aiRepository.generateContent(prompt).map { response ->
            parseCategorySuggestions(response)
        }
    }

    private fun buildCategorizationPrompt(manga: Manga): String {
        val standardCategories = StandardCategories.ALL_STANDARD.joinToString(", ")
        
        return buildString {
            appendLine("Analyze this manga and suggest relevant categories from the predefined list.")
            appendLine()
            appendLine("Manga Title: ${manga.title}")
            appendLine("Description: ${manga.description ?: "Not available"}")
            appendLine("Existing Genres: ${manga.genre.joinToString(", ")}")
            appendLine("Author: ${manga.author ?: "Unknown"}")
            appendLine("Artist: ${manga.artist ?: "Unknown"}")
            appendLine("Status: ${manga.status}")
            appendLine()
            appendLine("Available categories to choose from:")
            appendLine("GENRES: ${StandardCategories.GENRES.joinToString(", ")}")
            appendLine("DEMOGRAPHICS: ${StandardCategories.DEMOGRAPHICS.joinToString(", ")}")
            appendLine("THEMES: ${StandardCategories.THEMES.joinToString(", ")}")
            appendLine("TROPES: ${StandardCategories.TROPES.joinToString(", ")}")
            appendLine()
            appendLine("Instructions:")
            appendLine("1. Select 3-7 most relevant categories from the list above")
            appendLine("2. For each suggestion, provide a confidence score (0.0-1.0)")
            appendLine("3. Consider the title, description, and existing genres")
            appendLine("4. Be selective - only suggest categories you're confident about")
            appendLine()
            appendLine("Response format (one category per line):")
            appendLine("CategoryName|ConfidenceScore")
            appendLine()
            appendLine("Example:")
            appendLine("Action|0.92")
            appendLine("Isekai|0.88")
            appendLine("Shounen|0.85")
        }
    }

    private fun parseCategorySuggestions(response: String): List<CategorySuggestion> {
        return response.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("|") }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 2) {
                    val name = parts[0].trim()
                    val confidence = parts[1].trim().toFloatOrNull() ?: 0.5f
                    val type = StandardCategories.getCategoryType(name)
                    CategorySuggestion(
                        categoryName = name,
                        confidenceScore = confidence.coerceIn(0f, 1f),
                        categoryType = type
                    )
                } else {
                    null
                }
            }
            .sortedByDescending { it.confidenceScore }
            .take(7) // Limit to top 7 suggestions
    }
}
