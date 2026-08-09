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

/**
 * A row in the feed's source list, plus whether an installed source actually owns its key.
 *
 * [isInstalled] is false in two cases that cannot be told apart from the row alone: the source's
 * extension has been uninstalled, or the row is a leftover from when the sheet hashed free text
 * into an id (which matched no source, then or now). Both are dead weight the user can only act
 * on if the UI admits it, and the right action — delete it — is the same either way, so they are
 * shown identically rather than guessed between.
 */
@Immutable
data class SavedFeedSourceRow(
    val source: FeedSource,
    val isInstalled: Boolean,
)

data class SavedFeedState(
    val sources: List<SavedFeedSourceRow> = emptyList(),
    /**
     * Installed sources not already in the feed.
     *
     * Excluding the ones already added is not just tidiness: `insertFeedSource` is
     * `OnConflictStrategy.REPLACE` against a unique index on `sourceId`, so re-adding a source
     * deletes the existing row and inserts a fresh one — silently resetting the user's enabled
     * toggle, item count and ordering back to defaults.
     *
     * A legacy row keyed off a hashed *name* does not suppress its source here, because its key
     * matches nothing — so the source stays addable, which is what the user wants. The legacy row
     * is surfaced separately as not-installed rather than deduplicated away, since matching it by
     * display name would be the same guessing that produced it.
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
