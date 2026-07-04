package app.otakureader.feature.library.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.DynamicCategoryRepository
import app.otakureader.domain.usecase.CreateCategoryUseCase
import app.otakureader.domain.usecase.DeleteCategoryUseCase
import app.otakureader.domain.usecase.ToggleCategoryHiddenUseCase
import app.otakureader.domain.usecase.ToggleCategoryLockedUseCase
import app.otakureader.domain.usecase.ToggleCategoryNsfwUseCase
import app.otakureader.domain.usecase.UpdateCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import app.otakureader.domain.model.DynamicCategoryRule
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val dynamicCategoryRepository: DynamicCategoryRepository,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val toggleCategoryHiddenUseCase: ToggleCategoryHiddenUseCase,
    private val toggleCategoryNsfwUseCase: ToggleCategoryNsfwUseCase,
    private val toggleCategoryLockedUseCase: ToggleCategoryLockedUseCase,
    private val libraryPreferences: LibraryPreferences,
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

            combine(
                categoryRepository.getCategories(),
                libraryPreferences.skipUpdateCategoryIds,
            ) { categories, skipIds -> categories to skipIds }
                .collect { (categories, skipIds) ->
                    val items = categories.map { category ->
                        // Count manga in category
                        val mangaIds = categoryRepository.getMangaIdsByCategoryId(category.id).first()

                        CategoryUiItem(
                            id = category.id,
                            name = category.name,
                            mangaCount = mangaIds.size,
                            isHidden = category.isHidden,
                            isNsfw = category.isNsfw,
                            isLocked = category.isLocked,
                            isDynamic = dynamicCategoryRepository.hasDynamicRules(category.id),
                            updateFrequency = category.updateFrequency,
                            skipUpdates = category.id.toString() in skipIds,
                        )
                    }.sortedBy { it.name }

                    _state.value = _state.value.copy(
                        categories = items,
                        isLoading = false,
                    )
                }
        }
    }

    fun onEvent(event: CategoryEvent) {
        when (event) {
            is CategoryEvent.CreateCategory -> createCategory(event)
            is CategoryEvent.UpdateCategory -> updateCategory(event)
            is CategoryEvent.DeleteCategory -> deleteCategory(event.categoryId)
            is CategoryEvent.ToggleHidden -> toggleHidden(event.categoryId)
            is CategoryEvent.ToggleNsfw -> toggleNsfw(event.categoryId)
            is CategoryEvent.ToggleLocked -> toggleLocked(event.categoryId)
            is CategoryEvent.ToggleSkipUpdates -> toggleSkipUpdates(event.categoryId)
            is CategoryEvent.OpenRuleEditor -> openRuleEditor(event.categoryId)
            is CategoryEvent.CloseRuleEditor ->
                _state.update { it.copy(ruleEditor = null) }
            is CategoryEvent.AddRule -> addRuleToEditor(event.rule)
            is CategoryEvent.RemoveRule -> removeRuleFromEditor(event.index)
            is CategoryEvent.SaveRules -> saveRulesFromEditor()
            is CategoryEvent.SetHiddenRevealed ->
                _state.value = _state.value.copy(hiddenRevealed = event.revealed)
        }
    }

    private fun createCategory(event: CategoryEvent.CreateCategory) {
        viewModelScope.launch {
            try {
                createCategoryUseCase(event.name, event.frequency)
                _effect.emit(CategoryEffect.DismissDialog)
                _effect.emit(CategoryEffect.ShowSnackbar("Category created"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to create category: ${e.message}"))
            }
        }
    }

    private fun updateCategory(event: CategoryEvent.UpdateCategory) {
        viewModelScope.launch {
            try {
                updateCategoryUseCase(event.categoryId, event.name, event.frequency)
                _effect.emit(CategoryEffect.DismissDialog)
                _effect.emit(CategoryEffect.ShowSnackbar("Category updated"))
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to delete category: ${e.message}"))
            }
        }
    }

    private fun toggleHidden(categoryId: Long) {
        viewModelScope.launch {
            try {
                toggleCategoryHiddenUseCase(categoryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to toggle hidden: ${e.message}"))
            }
        }
    }

    private fun toggleNsfw(categoryId: Long) {
        viewModelScope.launch {
            try {
                toggleCategoryNsfwUseCase(categoryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to toggle NSFW: ${e.message}"))
            }
        }
    }

    private fun toggleLocked(categoryId: Long) {
        viewModelScope.launch {
            try {
                toggleCategoryLockedUseCase(categoryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to toggle lock: ${e.message}"))
            }
        }
    }

    private fun addRuleToEditor(rule: DynamicCategoryRule) {
        _state.update { st ->
            st.ruleEditor?.let { editor ->
                if (editor.rules.contains(rule)) st else {
                    st.copy(ruleEditor = editor.copy(rules = editor.rules + rule))
                }
            } ?: st
        }
    }

    private fun removeRuleFromEditor(index: Int) {
        _state.update { st ->
            st.ruleEditor?.let { editor ->
                st.copy(
                    ruleEditor = editor.copy(
                        rules = editor.rules.filterIndexed { i, _ -> i != index },
                    ),
                )
            } ?: st
        }
    }

    private fun saveRulesFromEditor() {
        val editor = _state.value.ruleEditor
        if (editor != null) {
            _state.update { it.copy(ruleEditor = null) }
            saveRules(editor)
        }
    }

    private fun toggleSkipUpdates(categoryId: Long) {
        viewModelScope.launch {
            try {
                val current = libraryPreferences.skipUpdateCategoryIds.first()
                val id = categoryId.toString()
                libraryPreferences.setSkipUpdateCategoryIds(
                    if (id in current) current - id else current + id,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to change update setting: ${e.message}"))
            }
        }
    }

    private fun openRuleEditor(categoryId: Long) {
        viewModelScope.launch {
            try {
                val name = _state.value.categories.find { it.id == categoryId }?.name.orEmpty()
                val rules = dynamicCategoryRepository.getRulesForCategory(categoryId).first()
                _state.update {
                    it.copy(ruleEditor = RuleEditorUiState(categoryId, name, rules))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to load smart rules: ${e.message}"))
            }
        }
    }

    private fun saveRules(editor: RuleEditorUiState) {
        viewModelScope.launch {
            try {
                dynamicCategoryRepository.setRules(editor.categoryId, editor.rules)
                // Reflect the new dynamic state immediately; the category list flow does not
                // re-emit on rule changes alone.
                _state.update { st ->
                    st.copy(
                        categories = st.categories.map {
                            if (it.id == editor.categoryId) {
                                it.copy(isDynamic = editor.rules.isNotEmpty())
                            } else {
                                it
                            }
                        },
                    )
                }
                _effect.emit(
                    CategoryEffect.ShowSnackbar(
                        if (editor.rules.isEmpty()) "Smart rules cleared" else "Smart rules saved",
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.emit(CategoryEffect.ShowSnackbar("Failed to save smart rules: ${e.message}"))
            }
        }
    }
}
