package app.otakureader.feature.settings.delegate

import android.util.Log
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.domain.scheduler.LibraryUpdateScheduler
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySettingsDelegate @Inject constructor(
    private val libraryPreferences: LibraryPreferences,
    private val generalPreferences: GeneralPreferences,
    private val libraryUpdateScheduler: LibraryUpdateScheduler,
) {

    // Keep latest values to use for rescheduling when the other changes
    private var latestUpdateCheckInterval: Int = 12
    private var latestUpdateOnlyOnWifi: Boolean = false
    private var latestUpdateRequireCharging: Boolean = false

    fun startObserving(
        scope: CoroutineScope,
        updateState: ((SettingsState) -> SettingsState) -> Unit,
    ) {
        scope.launch {
            combine(
                libraryPreferences.gridSize,
                libraryPreferences.showBadges,
                libraryPreferences.updateOnlyOnWifi,
                libraryPreferences.autoRefreshOnStart,
            ) { gridSize, showBadges, updateOnWifi, autoRefresh ->
                latestUpdateOnlyOnWifi = updateOnWifi
                updateState { it.copy(library = it.library.copy(
                    libraryGridSize = gridSize,
                    showBadges = showBadges,
                    updateOnlyOnWifi = updateOnWifi,
                    autoRefreshOnStart = autoRefresh,
                )) }
            }.collect { }
        }
        scope.launch {
            libraryPreferences.isStaggeredGrid.collect { staggered ->
                updateState { it.copy(library = it.library.copy(isStaggeredGrid = staggered)) }
            }
        }
        scope.launch {
            libraryPreferences.showDownloadBadge.collect { show ->
                updateState { it.copy(library = it.library.copy(showDownloadBadge = show)) }
            }
        }
        scope.launch {
            libraryPreferences.showUpdateProgress.collect { showProgress ->
                updateState { it.copy(library = it.library.copy(showUpdateProgress = showProgress)) }
            }
        }
        scope.launch {
            libraryPreferences.updateRequireCharging.collect { requireCharging ->
                latestUpdateRequireCharging = requireCharging
                updateState { it.copy(library = it.library.copy(updateRequireCharging = requireCharging)) }
            }
        }
        scope.launch {
            libraryPreferences.skipUpdatesWithUnread.collect { v ->
                updateState { it.copy(library = it.library.copy(skipUpdatesWithUnread = v)) }
            }
        }
        scope.launch {
            libraryPreferences.skipUpdatesWithCompleted.collect { v ->
                updateState { it.copy(library = it.library.copy(skipUpdatesWithCompleted = v)) }
            }
        }
        scope.launch {
            libraryPreferences.skipUpdatesNeverStarted.collect { v ->
                updateState { it.copy(library = it.library.copy(skipUpdatesNeverStarted = v)) }
            }
        }
        scope.launch {
            generalPreferences.updateCheckInterval.collect { interval ->
                latestUpdateCheckInterval = interval
                updateState { it.copy(updateCheckInterval = interval) }
            }
        }
    }

    suspend fun handleEvent(
        event: SettingsEvent,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ): Boolean = when (event) {
        is SettingsEvent.SetLibraryGridSize -> { libraryPreferences.setGridSize(event.size); true }
        is SettingsEvent.SetStaggeredGrid -> { libraryPreferences.setStaggeredGrid(event.staggered); true }
        is SettingsEvent.SetShowBadges -> { libraryPreferences.setShowBadges(event.enabled); true }
        is SettingsEvent.SetShowDownloadBadge -> { libraryPreferences.setShowDownloadBadge(event.enabled); true }
        is SettingsEvent.SetUpdateOnlyOnWifi -> {
            libraryPreferences.setUpdateOnlyOnWifi(event.enabled)
            latestUpdateOnlyOnWifi = event.enabled
            scheduleLibraryUpdateOrShowError(latestUpdateCheckInterval, event.enabled, latestUpdateRequireCharging, sendEffect)
            true
        }
        is SettingsEvent.SetUpdateRequireCharging -> {
            libraryPreferences.setUpdateRequireCharging(event.enabled)
            latestUpdateRequireCharging = event.enabled
            scheduleLibraryUpdateOrShowError(latestUpdateCheckInterval, latestUpdateOnlyOnWifi, event.enabled, sendEffect)
            true
        }
        is SettingsEvent.SetAutoRefreshOnStart -> { libraryPreferences.setAutoRefreshOnStart(event.enabled); true }
        is SettingsEvent.SetShowUpdateProgress -> { libraryPreferences.setShowUpdateProgress(event.enabled); true }
        is SettingsEvent.SetSkipUpdatesWithUnread -> { libraryPreferences.setSkipUpdatesWithUnread(event.enabled); true }
        is SettingsEvent.SetSkipUpdatesWithCompleted -> { libraryPreferences.setSkipUpdatesWithCompleted(event.enabled); true }
        is SettingsEvent.SetSkipUpdatesNeverStarted -> { libraryPreferences.setSkipUpdatesNeverStarted(event.enabled); true }
        is SettingsEvent.SetUpdateInterval -> {
            generalPreferences.setUpdateCheckInterval(event.hours)
            latestUpdateCheckInterval = event.hours
            scheduleLibraryUpdateOrShowError(event.hours, latestUpdateOnlyOnWifi, latestUpdateRequireCharging, sendEffect)
            true
        }
        is SettingsEvent.SetNotificationsEnabled -> {
            // Library delegate also handles notification toggle (shared with Appearance)
            // Appearance delegate will handle the actual pref write; we just pass through
            false
        }
        else -> false
    }

    private suspend fun scheduleLibraryUpdateOrShowError(
        intervalHours: Int,
        wifiOnly: Boolean,
        requireCharging: Boolean,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ) {
        try {
            libraryUpdateScheduler.schedule(intervalHours = intervalHours, wifiOnly = wifiOnly, requireCharging = requireCharging)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(
                "LibrarySettingsDelegate",
                "Failed to schedule library update " +
                    "(intervalHours=$intervalHours, wifiOnly=$wifiOnly, requireCharging=$requireCharging)",
                e,
            )
            sendEffect(SettingsEffect.ShowSnackbar("Failed to update library scheduler settings"))
        }
    }
}
