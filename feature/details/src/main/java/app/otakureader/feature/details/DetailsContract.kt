package app.otakureader.feature.details

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.core.preferences.DeleteAfterReadMode
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus

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
        val error: String? = null,
        val isRefreshing: Boolean = false,
        val nextUnreadChapter: Chapter? = null,
        val deleteAfterReadOverride: DeleteAfterReadMode = DeleteAfterReadMode.INHERIT,
        val globalDeleteAfterRead: Boolean = false,
        val noteEditorVisible: Boolean = false,
        val noteEditorText: String = ""
    ) : UiState {
        
        val canStartReading: Boolean
            get() = chapters.isNotEmpty()
        
        val hasUnreadChapters: Boolean
            get() = chapters.any { !it.read }
        
        val sortedChapters: List<ChapterItem>
            get() = when (chapterSortOrder) {
                ChapterSortOrder.ASCENDING -> chapters.sortedBy { it.chapterNumber }
                ChapterSortOrder.DESCENDING -> chapters.sortedByDescending { it.chapterNumber }
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
     * Chapter item for UI with computed properties
     */
    data class ChapterItem(
        val id: Long,
        val mangaId: Long,
        val name: String,
        val chapterNumber: Float,
        val volume: String?,
        val scanlator: String?,
        val read: Boolean,
        val bookmark: Boolean,
        val lastPageRead: Int,
        val dateUpload: Long,
        val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED
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

        // Per-manga reader settings (#260)
        data class SetReaderDirection(val direction: Int?) : Event
        data class SetReaderMode(val mode: Int?) : Event
        data class SetReaderColorFilter(val filter: Int?) : Event
        data class SetReaderCustomTintColor(val color: Long?) : Event

        // Page preloading settings (#264)
        data class SetPreloadPagesBefore(val count: Int?) : Event
        data class SetPreloadPagesAfter(val count: Int?) : Event

        // Reset all per-manga reader overrides to defaults
        data object ResetReaderSettings : Event
    }

    /**
     * UI Effects (one-shot side effects)
     */
    sealed interface Effect : UiEffect {
        data class NavigateToReader(val mangaId: Long, val chapterId: Long) : Effect
        data class ShowSnackbar(val message: String) : Effect
        data class ShowError(val message: String) : Effect
        data class ShareManga(val title: String, val url: String) : Effect
        data class OpenInBrowser(val url: String) : Effect
    }
}

/**
 * Extension to convert domain Chapter to ChapterItem
 */
fun Chapter.toChapterItem(): DetailsContract.ChapterItem {
    // Extract volume from chapter name if available (e.g., "Vol. 1 Chapter 1")
    val volumeRegex = Regex("""Vol\.?\s*(\d+)""", RegexOption.IGNORE_CASE)
    val volume = volumeRegex.find(name)?.groupValues?.get(1)?.let { "Volume $it" }
    
    return DetailsContract.ChapterItem(
        id = id,
        mangaId = mangaId,
        name = name,
        chapterNumber = chapterNumber,
        volume = volume,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateUpload = dateUpload,
        downloadStatus = DetailsContract.DownloadStatus.NOT_DOWNLOADED
    )
}

/**
 * Extension to get display status text
 */
fun MangaStatus.displayText(): String = when (this) {
    MangaStatus.UNKNOWN -> "Unknown"
    MangaStatus.ONGOING -> "Ongoing"
    MangaStatus.COMPLETED -> "Completed"
    MangaStatus.LICENSED -> "Licensed"
    MangaStatus.PUBLISHING_FINISHED -> "Publishing Finished"
    MangaStatus.CANCELLED -> "Cancelled"
    MangaStatus.ON_HIATUS -> "On Hiatus"
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
