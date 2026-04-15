package app.otakureader.feature.library.category

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState

data class CategoryUiItem(
    val id: Long,
    val name: String,
    val mangaCount: Int,
    val isHidden: Boolean,
    val isNsfw: Boolean
)

data class CategoryManagementState(
    val categories: List<CategoryUiItem> = emptyList(),
    val isLoading: Boolean = false
) : UiState

sealed interface CategoryEvent : UiEvent {
    data class CreateCategory(val name: String) : CategoryEvent
    data class UpdateCategory(val categoryId: Long, val name: String) : CategoryEvent
    data class DeleteCategory(val categoryId: Long) : CategoryEvent
    data class ToggleHidden(val categoryId: Long) : CategoryEvent
    data class ToggleNsfw(val categoryId: Long) : CategoryEvent
}

sealed interface CategoryEffect : UiEffect {
    data class ShowSnackbar(val message: String) : CategoryEffect
    data object DismissDialog : CategoryEffect
}
