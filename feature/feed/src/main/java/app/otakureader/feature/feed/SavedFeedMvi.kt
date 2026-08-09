package app.otakureader.feature.feed

import androidx.compose.runtime.Immutable
import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.FeedSource

/**
 * An installed source the user could add to their feed.
 *
 * [sourceId] is the canonical key (`MangaSource.id.toSourceId()`), the same value every manga row
 * stores. Before this existed the sheet took free text and hashed whatever was typed, so the id
 * it saved could not match a real source and a typo was indistinguishable from a correct entry.
 */
@Immutable
data class FeedSourceOption(
    val sourceId: Long,
    val name: String,
    val lang: String,
)

data class SavedFeedState(
    val sources: List<FeedSource> = emptyList(),
    /**
     * Installed sources not already in the feed.
     *
     * Excluding the ones already added is not just tidiness: `insertFeedSource` is
     * `OnConflictStrategy.REPLACE` against a unique index on `sourceId`, so re-adding a source
     * deletes the existing row and inserts a fresh one — silently resetting the user's enabled
     * toggle, item count and ordering back to defaults.
     */
    val availableSources: List<FeedSourceOption> = emptyList(),
    val isLoading: Boolean = false,
) : UiState

sealed interface SavedFeedEvent : UiEvent {
    data class AddSource(val sourceId: Long, val sourceName: String) : SavedFeedEvent
    data class RemoveSource(val sourceId: Long) : SavedFeedEvent
    data class ToggleSource(val sourceId: Long, val enabled: Boolean) : SavedFeedEvent
}

sealed interface SavedFeedEffect : UiEffect {
    data class ShowSnackbar(val message: String) : SavedFeedEffect
}
