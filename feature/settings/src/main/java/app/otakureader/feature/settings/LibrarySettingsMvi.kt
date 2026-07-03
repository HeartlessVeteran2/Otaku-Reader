@file:Suppress("MatchingDeclarationName")

package app.otakureader.feature.settings

data class LibrarySettingsState(
    val libraryGridSize: Int = 3,
    val isStaggeredGrid: Boolean = false,
    val showBadges: Boolean = true,
    val showDownloadBadge: Boolean = true,
    val updateOnlyOnWifi: Boolean = false,
    val autoRefreshOnStart: Boolean = false,
    val showUpdateProgress: Boolean = true,
    val skipUpdatesWithUnread: Boolean = false,
    val skipUpdatesWithCompleted: Boolean = false,
    val skipUpdatesNeverStarted: Boolean = false,
)
