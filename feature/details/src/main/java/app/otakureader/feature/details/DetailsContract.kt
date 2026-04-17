package app.otakureader.feature.details

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.core.preferences.DeleteAfterReadMode
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import androidx.annotation.StringRes

/**
 * Contract for MVI pattern in Details Screen
 */
object DetailsContract {

    /**
     * UI State for Details Screen
     */
    data class State(
        val isLoading: Boolean = true,
        val manga: Manga? = null,
        val chapters: List<ChapterItem> = emptyList(),
        val selectedChapters: Set<Long> = emptySet(),
        val isFavorite: Boolean = false,
        val descriptionExpanded: Boolean = false,
        val chapterSortOrder: ChapterSortOrder = ChapterSortOrder.DESCENDING,
        /** Active chapter list filter. */
        val chapterFilter: ChapterFilter = ChapterFilter(),
        val error: String? = null,
        val isRefreshing: Boolean = false,
        val nextUnreadChapter: Chapter? = null,
        val deleteAfterReadOverride: DeleteAfterReadMode = DeleteAfterReadMode.INHERIT,
        val globalDeleteAfterRead: Boolean = false,
        val noteEditorVisible: Boolean = false,
        val noteEditorText: String = "",

        /** AI-generated chapter summaries keyed by chapter ID. */
        val chapterSummaries: Map<Long, String> = emptyMap(),
        /** AI-generated summary of the manga description; null when not yet generated. */
        val aiSummary: String? = null,
        /** True while the AI summary is being generated. */
        val isGeneratingSummary: Boolean = false,
        /** Whether the AI summary translation feature is enabled in settings. */
        val aiSummaryEnabled: Boolean = false,
        /** Source suggestions (related titles from the source website). */
        val sourceSuggestions: List<SourceSuggestion> = emptyList(),
        /** True while loading source suggestions. */
        val isLoadingSourceSuggestions: Boolean = false,
        /** Error message for source suggestions loading failure. */
        val sourceSuggestionsError: String? = null,
        /** Whether the chapter filter bottom-sheet is currently visible. */
        val showChapterFilter: Boolean = false,
        /** Whether to show panorama cover (wide banner) instead of square thumbnail. */
        val showPanoramaCover: Boolean = false
    ) : UiState {
        
        val canStartReading: Boolean
            get() = chapters.isNotEmpty()
        
        val hasUnreadChapters: Boolean
            get() = chapters.any { !it.read }
        
        val sortedChapters: List<ChapterItem>
            get() {
                val filtered = chapterFilter.apply(chapters)
                return when (chapterSortOrder) {
                    ChapterSortOrder.ASCENDING -> filtered.sortedBy { it.chapterNumber }
                    ChapterSortOrder.DESCENDING -> filtered.sortedByDescending { it.chapterNumber }
                }
            }
        
        val groupedChapters: Map<String?, List<ChapterItem>>
            get() = sortedChapters.groupBy { it.volume }

        val isDeleteAfterReadEnabled: Boolean
            get() = when (deleteAfterReadOverride) {
                DeleteAfterReadMode.ENABLED -> true
                DeleteAfterReadMode.DISABLED -> false
                DeleteAfterReadMode.INHERIT -> globalDeleteAfterRead
            }
    }

    /**
     * Tri-state for chapter list filters: unset = show all, true = show only matching,
     * false = show only non-matching.
     */
    enum class TriState { ALL, ONLY, EXCLUDE }

    /**
     * Active chapter list filter configuration, matching Mihon's filter sheet options.
     */
    data class ChapterFilter(
        val read: TriState = TriState.ALL,
        val bookmarked: TriState = TriState.ALL,
        val downloaded: TriState = TriState.ALL,
        /** When non-null, only chapters from this scanlator are shown. */
        val scanlator: String? = null
    ) {
        val isActive: Boolean
            get() = read != TriState.ALL || bookmarked != TriState.ALL ||
                    downloaded != TriState.ALL || scanlator != null

        fun apply(chapters: List<ChapterItem>): List<ChapterItem> = chapters.filter { ch ->
            val readOk = when (read) {
                TriState.ALL -> true
                TriState.ONLY -> ch.read
                TriState.EXCLUDE -> !ch.read
            }
            val bookmarkOk = when (bookmarked) {
                TriState.ALL -> true
                TriState.ONLY -> ch.bookmark
                TriState.EXCLUDE -> !ch.bookmark
            }
            val downloadOk = when (downloaded) {
                TriState.ALL -> true
                TriState.ONLY -> ch.downloadStatus == DownloadStatus.DOWNLOADED
                TriState.EXCLUDE -> ch.downloadStatus != DownloadStatus.DOWNLOADED
            }
            val scanlatorOk = scanlator == null || ch.scanlator == scanlator
            readOk && bookmarkOk && downloadOk && scanlatorOk
        }
    }

    /**
     * Chapter item for UI with computed properties
     */
    data class ChapterItem(
        val id: Long,
        val mangaId: Long,
        val url: String,
        val name: String,
        val chapterNumber: Float,
        val volume: String?,
        val scanlator: String?,
        val read: Boolean,
        val bookmark: Boolean,
        val lastPageRead: Int,
        val dateUpload: Long,
        val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
        val thumbnailUrl: String? = null,
        val totalPages: Int = 0
    )

    /**
     * Download status for chapters
     */
    enum class DownloadStatus {
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED
    }

    /**
     * Chapter sort order options
     */
    enum class ChapterSortOrder {
        ASCENDING,
        DESCENDING
    }

    /**
     * UI Events (user actions)
     */
    sealed interface Event : UiEvent {
        data object Refresh : Event
        data object ToggleFavorite : Event
        data object ToggleDescription : Event
        data object ToggleSortOrder : Event
        data object ShowChapterFilter : Event
        data object HideChapterFilter : Event
        data class SetChapterFilter(val filter: ChapterFilter) : Event
        data object StartReading : Event
        data object ContinueReading : Event
        data class ChapterClick(val chapterId: Long) : Event
        data class ChapterLongClick(val chapterId: Long) : Event
        data class ToggleChapterRead(val chapterId: Long) : Event
        data class ToggleChapterBookmark(val chapterId: Long) : Event
        data class DownloadChapter(val chapterId: Long) : Event
        data class DeleteChapterDownload(val chapterId: Long) : Event
        data class ExportChapterAsCbz(val chapterId: Long) : Event
        data class MarkPreviousAsRead(val chapterId: Long) : Event
        data object ShareManga : Event
        data class SetDeleteAfterReadOverride(val mode: DeleteAfterReadMode) : Event
        data object ShowNoteEditor : Event
        data object HideNoteEditor : Event
        data class UpdateNoteText(val text: String) : Event
        data object SaveNote : Event
        data object ClearChapterSelection : Event
        data object SelectAllChapters : Event
        data object DownloadSelectedChapters : Event
        data object DeleteSelectedChapters : Event
        data object MarkSelectedAsRead : Event
        data object MarkSelectedAsUnread : Event
        data object BookmarkSelectedChapters : Event
        data object ToggleNotifications : Event
        data object OpenTracking : Event

        // Per-manga reader settings (#260)
        data class SetReaderDirection(val direction: Int?) : Event
        data class SetReaderMode(val mode: Int?) : Event
        data class SetReaderColorFilter(val filter: Int?) : Event
        data class SetReaderCustomTintColor(val color: Long?) : Event
        data class SetReaderBackgroundColor(val color: Long?) : Event

        // Page preloading settings (#264)
        data class SetPreloadPagesBefore(val count: Int?) : Event
        data class SetPreloadPagesAfter(val count: Int?) : Event
        
        // Chapter thumbnail loading
        data class LoadChapterThumbnail(val chapterId: Long) : Event

        // AI Summary
        data class RequestChapterSummary(val chapterId: Long) : Event
        // AI Summary Translation
        data object GenerateAiSummary : Event
        // Source suggestions
        data object LoadSourceSuggestions : Event
        data class OnSourceSuggestionClick(val suggestion: SourceSuggestion) : Event
        
        // Panorama cover
        data object TogglePanoramaCover : Event
    }

    /**
     * UI Effects (one-shot side effects)
     */
    sealed interface Effect : UiEffect {
        data class NavigateToReader(val mangaId: Long, val chapterId: Long) : Effect
        data class ShowSnackbar(val message: String) : Effect
        data class ShowError(val message: String) : Effect
        data class ShareManga(val title: String, val url: String) : Effect
        data class NavigateToTracking(val mangaId: Long, val mangaTitle: String) : Effect
        data class OpenInBrowser(val url: String) : Effect
        data class NavigateToGlobalSearch(val query: String) : Effect
    }
}

/**
 * Extension to convert domain Chapter to ChapterItem
 */
fun Chapter.toChapterItem(thumbnailUrl: String? = null, totalPages: Int = 0): DetailsContract.ChapterItem {
    // Extract volume from chapter name if available (e.g., "Vol. 1 Chapter 1")
    val volumeRegex = Regex("""Vol\.?\s*(\d+)""", RegexOption.IGNORE_CASE)
    val volume = volumeRegex.find(name)?.groupValues?.get(1)?.let { "Volume $it" }
    
    return DetailsContract.ChapterItem(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        chapterNumber = chapterNumber,
        volume = volume,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateUpload = dateUpload,
        downloadStatus = DetailsContract.DownloadStatus.NOT_DOWNLOADED,
        thumbnailUrl = thumbnailUrl,
        totalPages = totalPages
    )
}

/**
 * Extension to get display status text resource ID
 */
@StringRes
fun MangaStatus.displayTextResId(): Int = when (this) {
    MangaStatus.UNKNOWN -> R.string.details_status_unknown
    MangaStatus.ONGOING -> R.string.details_status_ongoing
    MangaStatus.COMPLETED -> R.string.details_status_completed
    MangaStatus.LICENSED -> R.string.details_status_licensed
    MangaStatus.PUBLISHING_FINISHED -> R.string.details_status_publishing_finished
    MangaStatus.CANCELLED -> R.string.details_status_cancelled
    MangaStatus.ON_HIATUS -> R.string.details_status_on_hiatus
}

/**
 * Extension to get status color
 */
fun MangaStatus.colorValue(): androidx.compose.ui.graphics.Color = when (this) {
    MangaStatus.UNKNOWN -> androidx.compose.ui.graphics.Color.Gray
    MangaStatus.ONGOING -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    MangaStatus.COMPLETED -> androidx.compose.ui.graphics.Color(0xFF2196F3)
    MangaStatus.LICENSED -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    MangaStatus.PUBLISHING_FINISHED -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
    MangaStatus.CANCELLED -> androidx.compose.ui.graphics.Color(0xFFF44336)
    MangaStatus.ON_HIATUS -> androidx.compose.ui.graphics.Color(0xFFFFC107)
}
