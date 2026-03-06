package app.komikku.feature.reader

data class ReaderState(
    val isLoading: Boolean = false,
    val pages: List<String> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val chapterName: String = "",
    val error: String? = null,
)

sealed class ReaderEvent {
    data class OnPageChange(val page: Int) : ReaderEvent()
    data object OnBackClick : ReaderEvent()
}

sealed class ReaderEffect {
    data object NavigateBack : ReaderEffect()
}
