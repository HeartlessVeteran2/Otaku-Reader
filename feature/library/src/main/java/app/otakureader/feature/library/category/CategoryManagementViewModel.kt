package app.otakureader.feature.library.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.usecase.CreateCategoryUseCase
import app.otakureader.domain.usecase.DeleteCategoryUseCase
import app.otakureader.domain.usecase.ToggleCategoryHiddenUseCase
import app.otakureader.domain.usecase.ToggleCategoryNsfwUseCase
import app.otakureader.domain.usecase.UpdateCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val toggleCategoryHiddenUseCase: ToggleCategoryHiddenUseCase,
    private val toggleCategoryNsfwUseCase: ToggleCategoryNsfwUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryManagementState())
    val state: StateFlow<CategoryManagementState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<CategoryEffect>()
    val effect: SharedFlow<CategoryEffect> = _effect.asSharedFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            categoryRepository.getCategories()
                .collect { categories ->
                    val items = categories.map { category ->
                        // Count manga in category
                        val mangaIds = categoryRepository.getMangaIdsByCategoryId(category.id).first()

                        CategoryUiItem(
                            id = category.id,
                            name = category.name,
                            mangaCount = mangaIds.size,
                            isHidden = category.isHidden,
                            isNsfw = category.isNsfw
                        )
                    }.sortedBy { it.name }

                    _state.value = CategoryManagementState(
                        categories = items,
                        isLoading = false
                    )
                }
        }
    }

    fun onEvent(event: CategoryEvent) {
        when (event) {
            is CategoryEvent.CreateCategory -> createCategory(event.name)
            is CategoryEvent.UpdateCategory -> updateCategory(event.categoryId, event.name)
            is CategoryEvent.DeleteCategory -> deleteCategory(event.categoryId)
            is CategoryEvent.ToggleHidden -> toggleHidden(event.categoryId)
            is CategoryEvent.ToggleNsfw -> toggleNsfw(event.categoryId)
        }
    }

    private fun createCategory(name: String) {
        viewModelScope.launch {
            try {
                createCategoryUseCase(name)
                _effect.emit(CategoryEffect.DismissDialog)
                _effect.emit(CategoryEffect.ShowSnackbar("Category created"))
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to create category: ${e.message}"))
            }
        }
    }

    private fun updateCategory(categoryId: Long, name: String) {
        viewModelScope.launch {
            try {
                updateCategoryUseCase(categoryId, name)
                _effect.emit(CategoryEffect.DismissDialog)
                _effect.emit(CategoryEffect.ShowSnackbar("Category updated"))
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to update category: ${e.message}"))
            }
        }
    }

    private fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            try {
                deleteCategoryUseCase(categoryId)
                _effect.emit(CategoryEffect.ShowSnackbar("Category deleted"))
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to delete category: ${e.message}"))
            }
        }
    }

    private fun toggleHidden(categoryId: Long) {
        viewModelScope.launch {
            try {
                toggleCategoryHiddenUseCase(categoryId)
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to toggle hidden: ${e.message}"))
            }
        }
    }

    private fun toggleNsfw(categoryId: Long) {
        viewModelScope.launch {
            try {
                toggleCategoryNsfwUseCase(categoryId)
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to toggle NSFW: ${e.message}"))
            }
        }
    }
}
