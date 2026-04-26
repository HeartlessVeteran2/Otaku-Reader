package app.otakureader.feature.settings.delegate

import android.util.Log
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.data.worker.LibraryUpdateScheduler
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
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

    fun startObserving(
        scope: CoroutineScope,
        updateState: ((SettingsState) -> SettingsState) -> Unit,
    ) {
        scope.launch {
            combine(
                libraryPreferences.gridSize,
                libraryPreferences.showBadges,
                libraryPreferences.updateOnlyOnWifi,
                libraryPreferences.updateOnlyPinnedCategories,
                libraryPreferences.autoRefreshOnStart,
            ) { gridSize, showBadges, updateOnWifi, updatePinned, autoRefresh ->
                latestUpdateOnlyOnWifi = updateOnWifi
                updateState { it.copy(
                    libraryGridSize = gridSize,
                    showBadges = showBadges,
                    updateOnlyOnWifi = updateOnWifi,
                    updateOnlyPinnedCategories = updatePinned,
                    autoRefreshOnStart = autoRefresh,
                ) }
            }.collect { }
        }
        scope.launch {
            libraryPreferences.showUpdateProgress.collect { showProgress ->
                updateState { it.copy(showUpdateProgress = showProgress) }
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
        is SettingsEvent.SetShowBadges -> { libraryPreferences.setShowBadges(event.enabled); true }
        is SettingsEvent.SetUpdateOnlyOnWifi -> {
            libraryPreferences.setUpdateOnlyOnWifi(event.enabled)
            latestUpdateOnlyOnWifi = event.enabled
            scheduleLibraryUpdateOrShowError(latestUpdateCheckInterval, event.enabled, sendEffect)
            true
        }
        is SettingsEvent.SetUpdateOnlyPinnedCategories -> {
            libraryPreferences.setUpdateOnlyPinnedCategories(event.enabled)
            true
        }
        is SettingsEvent.SetAutoRefreshOnStart -> { libraryPreferences.setAutoRefreshOnStart(event.enabled); true }
        is SettingsEvent.SetShowUpdateProgress -> { libraryPreferences.setShowUpdateProgress(event.enabled); true }
        is SettingsEvent.SetUpdateInterval -> {
            generalPreferences.setUpdateCheckInterval(event.hours)
            latestUpdateCheckInterval = event.hours
            scheduleLibraryUpdateOrShowError(event.hours, latestUpdateOnlyOnWifi, sendEffect)
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
        sendEffect: suspend (SettingsEffect) -> Unit,
    ) {
        try {
            libraryUpdateScheduler.schedule(intervalHours = intervalHours, wifiOnly = wifiOnly)
        } catch (e: Exception) {
            Log.e("LibrarySettingsDelegate", "Failed to schedule library update (intervalHours=$intervalHours, wifiOnly=$wifiOnly)", e)
            sendEffect(SettingsEffect.ShowSnackbar("Failed to update library scheduler settings"))
        }
    }
}
