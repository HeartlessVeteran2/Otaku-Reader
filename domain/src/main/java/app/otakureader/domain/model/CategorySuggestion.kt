package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents an AI-suggested category with confidence score.
 */
@Immutable
data class CategorySuggestion(
    val categoryName: String,
    val confidenceScore: Float,
    val categoryType: CategoryType
)

/**
 * Types of categories that can be suggested.
 */
enum class CategoryType {
    GENRE,           // Action, Romance, Comedy, Drama, etc.
    DEMOGRAPHIC,     // Shounen, Seinen, Josei, Shoujo
    THEME,           // Isekai, School Life, Workplace, Fantasy, Sci-Fi
    TROPE,           // OP MC, Harem, Slow Burn, Slice of Life
    CUSTOM           // User-defined categories
}

/**
 * Result of AI categorization for a manga.
 */
@Immutable
data class CategorizationResult(
    val mangaId: Long,
    val suggestions: List<CategorySuggestion>,
    val appliedCategories: List<String> = emptyList(),
    val wasAutoApplied: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Predefined standard categories for auto-categorization.
 */
object StandardCategories {
    
    val GENRES = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
        "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life",
        "Sports", "Supernatural", "Thriller", "Tragedy"
    )
    
    val DEMOGRAPHICS = listOf(
        "Shounen", "Seinen", "Shoujo", "Josei", "Kodomo"
    )
    
    val THEMES = listOf(
        "Isekai", "School Life", "Workplace", "Martial Arts", "Mecha",
        "Military", "Music", "Parody", "Police", "Post-Apocalyptic",
        "Reverse Isekai", "Space", "Super Power", "Vampire", "Video Games",
        "Virtual Reality", "Zombies", "Historical", "Cooking"
    )
    
    val TROPES = listOf(
        "OP MC", "Harem", "Reverse Harem", "Slow Burn", "Enemies to Lovers",
        "Childhood Friends", "Time Travel", "Reincarnation", "Survival",
        "Academy", "Guild", "Dungeon", "Leveling System", "Betrayal",
        "Redemption", "Coming of Age", "Underdog", "Power Couple"
    )
    
    val ALL_STANDARD = GENRES + DEMOGRAPHICS + THEMES + TROPES
    
    /**
     * Get the category type for a given category name.
     */
    fun getCategoryType(name: String): CategoryType {
        return when {
            GENRES.any { it.equals(name, ignoreCase = true) } -> CategoryType.GENRE
            DEMOGRAPHICS.any { it.equals(name, ignoreCase = true) } -> CategoryType.DEMOGRAPHIC
            THEMES.any { it.equals(name, ignoreCase = true) } -> CategoryType.THEME
            TROPES.any { it.equals(name, ignoreCase = true) } -> CategoryType.TROPE
            else -> CategoryType.CUSTOM
        }
    }
}
