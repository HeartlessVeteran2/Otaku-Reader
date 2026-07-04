package app.otakureader.feature.library.category

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.CategoryUpdateFrequency
import app.otakureader.domain.model.DynamicCategoryRule

data class CategoryUiItem(
    val id: Long,
    val name: String,
    val mangaCount: Int,
    val isHidden: Boolean,
    val isNsfw: Boolean,
    val isLocked: Boolean = false,
    val isDynamic: Boolean = false,
    val updateFrequency: CategoryUpdateFrequency = CategoryUpdateFrequency.DAILY,
    /** Whether this category is excluded from global library updates. */
    val skipUpdates: Boolean = false,
)

/** In-progress smart-rule editing for a single category (null when the editor is closed). */
data class RuleEditorUiState(
    val categoryId: Long,
    val categoryName: String,
    val rules: List<DynamicCategoryRule> = emptyList(),
)

data class CategoryManagementState(
    val categories: List<CategoryUiItem> = emptyList(),
    val isLoading: Boolean = false,
    /** Whether hidden categories are currently revealed (after biometric unlock). */
    val hiddenRevealed: Boolean = false,
    /** Non-null while the user is editing a category's smart rules. */
    val ruleEditor: RuleEditorUiState? = null,
) : UiState {
    /** True when at least one category is hidden (so the reveal control is worth showing). */
    val hasHiddenCategories: Boolean get() = categories.any { it.isHidden }
}

sealed interface CategoryEvent : UiEvent {
    data class CreateCategory(
        val name: String,
        val frequency: CategoryUpdateFrequency = CategoryUpdateFrequency.DAILY,
    ) : CategoryEvent
    data class UpdateCategory(
        val categoryId: Long,
        val name: String,
        val frequency: CategoryUpdateFrequency,
    ) : CategoryEvent
    data class DeleteCategory(val categoryId: Long) : CategoryEvent
    data class ToggleHidden(val categoryId: Long) : CategoryEvent
    data class ToggleNsfw(val categoryId: Long) : CategoryEvent
    data class ToggleLocked(val categoryId: Long) : CategoryEvent
    /** Include/exclude this category from global library updates. */
    data class ToggleSkipUpdates(val categoryId: Long) : CategoryEvent
    /** Open the smart-rule editor for a category, loading its current rules. */
    data class OpenRuleEditor(val categoryId: Long) : CategoryEvent
    /** Discard the open rule editor without saving. */
    data object CloseRuleEditor : CategoryEvent
    /** Append a rule to the open editor (not persisted until [SaveRules]). */
    data class AddRule(val rule: DynamicCategoryRule) : CategoryEvent
    /** Remove the rule at [index] from the open editor. */
    data class RemoveRule(val index: Int) : CategoryEvent
    /** Persist the open editor's rules; an empty list makes the category non-dynamic. */
    data object SaveRules : CategoryEvent
    /** Reveal or re-hide hidden categories (reveal should be gated by biometric in the UI). */
    data class SetHiddenRevealed(val revealed: Boolean) : CategoryEvent
}

sealed interface CategoryEffect : UiEffect {
    data class ShowSnackbar(val message: String) : CategoryEffect
    data object DismissDialog : CategoryEffect
}
