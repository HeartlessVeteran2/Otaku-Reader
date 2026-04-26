package app.otakureader.feature.tracking

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.SyncStatus
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerSyncState

data class TrackingState(
    val trackers: List<TrackerUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTracker: Int? = null,
    val searchQuery: String = "",
    val searchResults: List<TrackEntry> = emptyList(),
    val isSearching: Boolean = false,
    val mangaId: Long = 0L,
    val mangaTitle: String = "",
    val currentEntry: TrackEntry? = null,
    /** Tracker ID for which the credential login dialog should be shown. */
    val loginDialogTrackerId: Int? = null,
    /** Non-null when a sync conflict needs manual resolution. */
    val conflictState: ConflictUiState? = null,
    /** Per-tracker sync states keyed by tracker ID. */
    val syncStates: Map<Int, TrackerSyncState> = emptyMap()
) : UiState

/**
 * Holds information about an unresolved sync conflict shown to the user.
 */
data class ConflictUiState(
    val trackerId: Int,
    val trackerName: String,
    val localChapter: Float,
    val remoteChapter: Float,
    val message: String
)

data class TrackerUiModel(
    val id: Int,
    val name: String,
    /** Brand color (ARGB) used to render the tracker badge locally. */
    val brandColor: Long,
    val isLoggedIn: Boolean,
    val entry: TrackEntry? = null,
    /** Current sync status for this tracker, or null if no sync state exists. */
    val syncStatus: SyncStatus? = null
)

sealed interface TrackingEvent : UiEvent {
    data class LoadTrackers(val mangaId: Long, val mangaTitle: String) : TrackingEvent
    /** Initiates the appropriate login flow (credential dialog or OAuth). */
    data class InitiateLogin(val trackerId: Int) : TrackingEvent
    /** Submits credentials collected from the login dialog. */
    data class Login(val trackerId: Int, val username: String, val password: String) : TrackingEvent
    data object DismissLoginDialog : TrackingEvent
    data class Logout(val trackerId: Int) : TrackingEvent
    /** Opens the search dialog for a tracker without triggering a search yet. */
    data class OpenSearchDialog(val trackerId: Int) : TrackingEvent
    data class Search(val trackerId: Int, val query: String) : TrackingEvent
    data class LinkManga(val trackerId: Int, val remoteId: Long) : TrackingEvent
    data class UnlinkManga(val trackerId: Int) : TrackingEvent
    data class UpdateStatus(val trackerId: Int, val status: TrackStatus) : TrackingEvent
    data class UpdateProgress(val trackerId: Int, val chapter: Float) : TrackingEvent
    data class UpdateScore(val trackerId: Int, val score: Float) : TrackingEvent
    data class OnSearchQueryChange(val query: String) : TrackingEvent
    data object ClearSearch : TrackingEvent
    /** Triggers a manual bidirectional sync for a specific tracker. */
    data class SyncTracker(val trackerId: Int) : TrackingEvent
    /** Manually push local changes to a specific tracker. */
    data class PushToTracker(val trackerId: Int) : TrackingEvent
    /** Manually pull remote changes from a specific tracker. */
    data class PullFromTracker(val trackerId: Int) : TrackingEvent
    /** Resolves a detected conflict; [useLocal] = true keeps local, false keeps remote. */
    data class ResolveConflict(val trackerId: Int, val useLocal: Boolean) : TrackingEvent
    /** Dismisses the conflict dialog without resolving. */
    data object DismissConflict : TrackingEvent
}

sealed interface TrackingEffect : UiEffect {
    data class ShowMessage(val message: String) : TrackingEffect
    data class ShowError(val message: String) : TrackingEffect
    data class OpenOAuth(val trackerId: Int, val url: String) : TrackingEffect
}
