package app.otakureader.feature.library.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.entity.CategoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryDao: CategoryDao
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

            categoryDao.getCategories()
                .collect { entities ->
                    val items = entities.map { entity ->
                        // Count manga in category
                        val mangaIds = categoryDao.getMangaIdsByCategoryId(entity.id).first()

                        CategoryUiItem(
                            id = entity.id,
                            name = entity.name,
                            mangaCount = mangaIds.size,
                            isHidden = entity.flags and 1 != 0,
                            isNsfw = entity.flags and 2 != 0
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
                val maxOrder = categoryDao.getMaxCategoryOrder()
                val entity = CategoryEntity(
                    id = 0, // Auto-generated
                    name = name.trim(),
                    order = maxOrder + 1,
                    flags = 0
                )
                categoryDao.insert(entity)
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
                val entity = categoryDao.getCategoryById(categoryId)
                if (entity != null) {
                    categoryDao.update(entity.copy(name = name.trim()))
                    _effect.emit(CategoryEffect.DismissDialog)
                    _effect.emit(CategoryEffect.ShowSnackbar("Category updated"))
                }
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to update category: ${e.message}"))
            }
        }
    }

    private fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            try {
                categoryDao.deleteById(categoryId)
                _effect.emit(CategoryEffect.ShowSnackbar("Category deleted"))
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to delete category: ${e.message}"))
            }
        }
    }

    private fun toggleHidden(categoryId: Long) {
        viewModelScope.launch {
            try {
                categoryDao.toggleHiddenFlag(categoryId)
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to toggle hidden: ${e.message}"))
            }
        }
    }

    private fun toggleNsfw(categoryId: Long) {
        viewModelScope.launch {
            try {
                categoryDao.toggleNsfwFlag(categoryId)
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to toggle NSFW: ${e.message}"))
            }
        }
    }
}
