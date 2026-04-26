package app.otakureader.feature.settings.delegate

import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadSettingsDelegate @Inject constructor(
    private val downloadPreferences: DownloadPreferences,
) {

    fun startObserving(
        scope: CoroutineScope,
        updateState: ((SettingsState) -> SettingsState) -> Unit,
    ) {
        scope.launch {
            combine(
                downloadPreferences.deleteAfterReading,
                downloadPreferences.saveAsCbz,
                downloadPreferences.autoDownloadEnabled,
                downloadPreferences.downloadOnlyOnWifi,
                downloadPreferences.autoDownloadLimit,
            ) { deleteAfter, saveCbz, autoDownload, dlWifi, dlLimit ->
                updateState { it.copy(
                    deleteAfterReading = deleteAfter,
                    saveAsCbz = saveCbz,
                    autoDownloadEnabled = autoDownload,
                    downloadOnlyOnWifi = dlWifi,
                    autoDownloadLimit = dlLimit,
                ) }
            }.collect { }
        }
        scope.launch {
            combine(
                downloadPreferences.concurrentDownloads,
                downloadPreferences.downloadAheadWhileReading,
                downloadPreferences.downloadAheadOnlyOnWifi,
                downloadPreferences.downloadLocation,
            ) { concurrent, dlAhead, dlAheadWifi, dlLocation ->
                updateState { it.copy(
                    concurrentDownloads = concurrent,
                    downloadAheadWhileReading = dlAhead,
                    downloadAheadOnlyOnWifi = dlAheadWifi,
                    downloadLocation = dlLocation,
                ) }
            }.collect { }
        }
    }

    suspend fun handleEvent(
        event: SettingsEvent,
        @Suppress("UNUSED_PARAMETER") sendEffect: suspend (SettingsEffect) -> Unit,
    ): Boolean = when (event) {
        is SettingsEvent.SetDeleteAfterReading -> { downloadPreferences.setDeleteAfterReading(event.enabled); true }
        is SettingsEvent.SetSaveAsCbz -> { downloadPreferences.setSaveAsCbz(event.enabled); true }
        is SettingsEvent.SetAutoDownloadEnabled -> { downloadPreferences.setAutoDownloadEnabled(event.enabled); true }
        is SettingsEvent.SetDownloadOnlyOnWifi -> { downloadPreferences.setDownloadOnlyOnWifi(event.enabled); true }
        is SettingsEvent.SetAutoDownloadLimit -> { downloadPreferences.setAutoDownloadLimit(event.limit); true }
        is SettingsEvent.SetConcurrentDownloads -> { downloadPreferences.setConcurrentDownloads(event.count); true }
        is SettingsEvent.SetDownloadAheadWhileReading -> { downloadPreferences.setDownloadAheadWhileReading(event.count); true }
        is SettingsEvent.SetDownloadAheadOnlyOnWifi -> { downloadPreferences.setDownloadAheadOnlyOnWifi(event.enabled); true }
        is SettingsEvent.SetDownloadLocation -> { downloadPreferences.setDownloadLocation(event.location); true }
        else -> false
    }
}
