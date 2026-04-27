package app.otakureader.domain.usecase

import app.otakureader.domain.repository.CategoryRepository
import javax.inject.Inject

class DeleteCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(categoryId: Long) {
        categoryRepository.deleteCategory(categoryId)
    }
}
