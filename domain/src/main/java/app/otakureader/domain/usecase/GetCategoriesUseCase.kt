package app.otakureader.domain.usecase

import app.otakureader.domain.model.Category
import app.otakureader.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for loading library categories with their manga counts.
 * Also provides [getMangaIdsForCategory] for category-based filtering in the Library screen.
 */
class GetCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    /**
     * Returns a flow of all categories with [Category.mangaCount] populated by combining each
     * category's entry with the live count of manga assigned to it.
     */
    operator fun invoke(): Flow<List<Category>> =
        categoryRepository.getCategories().flatMapLatest { categories ->
            if (categories.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    categories.map { category ->
                        categoryRepository.getMangaIdsByCategoryId(category.id)
                            .map { ids -> category.copy(mangaCount = ids.size) }
                    }
                ) { it.toList() }
            }
        }

    /**
     * Returns a flow of manga IDs assigned to [categoryId]. Used by the Library screen to
     * filter the displayed manga list when the user selects a category tab.
     */
    fun getMangaIdsForCategory(categoryId: Long): Flow<List<Long>> =
        categoryRepository.getMangaIdsByCategoryId(categoryId)
}
