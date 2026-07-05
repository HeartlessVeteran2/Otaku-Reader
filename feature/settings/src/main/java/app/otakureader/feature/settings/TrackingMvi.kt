@file:Suppress("MatchingDeclarationName")

package app.otakureader.feature.settings

data class TrackingSettingsState(
    val trackers: List<TrackerInfo> = emptyList(),
    val trackingLoginInProgress: Boolean = false,
    val batchSyncInProgress: Boolean = false,
    val batchSyncSummary: app.otakureader.domain.repository.TrackerSyncRepository.SyncSummary? = null,
    /** Per-tracker "sync progress when a chapter is finished" opt-out; missing = default true. */
    val syncOnChapterRead: Map<Int, Boolean> = emptyMap(),
)
