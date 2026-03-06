package app.komikku.feature.reader

import app.komikku.core.common.mvi.UiEffect
import app.komikku.core.common.mvi.UiEvent
import app.komikku.core.common.mvi.UiState
import app.komikku.domain.model.Chapter
import app.komikku.domain.model.Manga

data class ReaderState(
    val isLoading: Boolean = false,
    val manga: Manga? = null,
    val chapter: Chapter? = null,
    val pages: List<String> = emptyList(), // Image URLs
    val currentPage: Int = 0,
    val isMenuVisible: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.PAGED_LTR,
    val scaleType: ScaleType = ScaleType.FIT_PAGE,
    val error: String? = null
) : UiState

enum class ReadingMode { PAGED_LTR, PAGED_RTL, WEBTOON, CONTINUOUS_VERTICAL }
enum class ScaleType { FIT_PAGE, FIT_WIDTH, FIT_HEIGHT, ORIGINAL }

sealed interface ReaderEvent : UiEvent {
    data class OnPageChange(val page: Int) : ReaderEvent
    data object ToggleMenu : ReaderEvent
    data object NavigateToPreviousChapter : ReaderEvent
    data object NavigateToNextChapter : ReaderEvent
    data class SetReadingMode(val mode: ReadingMode) : ReaderEvent
    data class SetScaleType(val type: ScaleType) : ReaderEvent
}

sealed interface ReaderEffect : UiEffect {
    data object NavigateBack : ReaderEffect
    data class ShowSnackbar(val message: String) : ReaderEffect
}
