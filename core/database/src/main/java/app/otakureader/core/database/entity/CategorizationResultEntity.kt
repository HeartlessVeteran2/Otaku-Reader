package app.otakureader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing AI categorization results.
 */
@Entity(tableName = "categorization_results")
data class CategorizationResultEntity(
    @PrimaryKey
    val mangaId: Long,
    val suggestionsJson: String, // JSON array of CategorySuggestion
    val appliedCategoriesJson: String, // JSON array of applied category names
    val wasAutoApplied: Boolean,
    val wasReviewed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
