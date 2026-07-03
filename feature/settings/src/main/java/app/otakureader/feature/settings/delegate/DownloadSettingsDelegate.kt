package app.otakureader.feature.settings.delegate

import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadSettingsDelegate @Inject constructor(
    private val downloadPreferences: DownloadPreferences,
    private val generalPreferences: GeneralPreferences,
) {

    @Suppress("LongMethod")
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
                updateState { it.copy(downloads = it.downloads.copy(
                    deleteAfterReading = deleteAfter,
                    saveAsCbz = saveCbz,
                    autoDownloadEnabled = autoDownload,
                    downloadOnlyOnWifi = dlWifi,
                    autoDownloadLimit = dlLimit,
                )) }
            }.collect { }
        }
        scope.launch {
            combine(
                downloadPreferences.concurrentDownloads,
                downloadPreferences.downloadAheadWhileReading,
                downloadPreferences.downloadAheadOnlyOnWifi,
                downloadPreferences.downloadLocation,
            ) { concurrent, dlAhead, dlAheadWifi, dlLocation ->
                updateState { it.copy(downloads = it.downloads.copy(
                    concurrentDownloads = concurrent,
                    downloadAheadWhileReading = dlAhead,
                    downloadAheadOnlyOnWifi = dlAheadWifi,
                    downloadLocation = dlLocation,
                )) }
            }.collect { }
        }
        scope.launch {
            combine(
                generalPreferences.smartDownloadEnabled,
                generalPreferences.smartDownloadChaptersAhead,
                generalPreferences.smartDownloadThreshold,
                generalPreferences.smartDownloadWifiOnly,
                generalPreferences.smartDownloadFavoritesOnly,
            ) { enabled, ahead, threshold, wifiOnly, favoritesOnly ->
                updateState { it.copy(downloads = it.downloads.copy(
                    smartDownloadEnabled = enabled,
                    smartDownloadChaptersAhead = ahead,
                    smartDownloadThreshold = threshold,
                    smartDownloadWifiOnly = wifiOnly,
                    smartDownloadFavoritesOnly = favoritesOnly,
                )) }
            }.collect { }
        }
        scope.launch {
            generalPreferences.smartDownloadMinStorageMb.collect { mb ->
                updateState { it.copy(downloads = it.downloads.copy(smartDownloadMinStorageMb = mb)) }
            }
        }
        scope.launch {
            downloadPreferences.dataSaverEnabled.collect { enabled ->
                updateState { it.copy(downloads = it.downloads.copy(downloadDataSaverEnabled = enabled)) }
            }
        }
        scope.launch {
            combine(
                downloadPreferences.autoDownloadCategoryInclude,
                downloadPreferences.autoDownloadCategoryExclude,
            ) { include, exclude ->
                updateState { it.copy(downloads = it.downloads.copy(
                    autoDownloadCategoryInclude = include,
                    autoDownloadCategoryExclude = exclude,
                )) }
            }.collect { }
        }
        scope.launch {
            downloadPreferences.cbzEncryptionEnabled.collect { enabled ->
                updateState { it.copy(downloads = it.downloads.copy(cbzEncryptionEnabled = enabled)) }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    suspend fun handleEvent(
        event: SettingsEvent,
        sendEffect: suspend (SettingsEffect) -> Unit,
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
        is SettingsEvent.RequestDownloadLocationPicker -> {
            sendEffect(SettingsEffect.ShowDownloadLocationPicker(downloadPreferences.downloadLocation.first()))
            true
        }
        is SettingsEvent.SetSmartDownloadEnabled -> { generalPreferences.setSmartDownloadEnabled(event.enabled); true }
        is SettingsEvent.SetSmartDownloadChaptersAhead ->
            { generalPreferences.setSmartDownloadChaptersAhead(event.count); true }
        is SettingsEvent.SetSmartDownloadThreshold ->
            { generalPreferences.setSmartDownloadThreshold(event.threshold); true }
        is SettingsEvent.SetSmartDownloadWifiOnly -> { generalPreferences.setSmartDownloadWifiOnly(event.enabled); true }
        is SettingsEvent.SetSmartDownloadFavoritesOnly ->
            { generalPreferences.setSmartDownloadFavoritesOnly(event.enabled); true }
        is SettingsEvent.SetSmartDownloadMinStorageMb ->
            { generalPreferences.setSmartDownloadMinStorageMb(event.mb); true }
        is SettingsEvent.SetDownloadDataSaverEnabled ->
            { downloadPreferences.setDataSaverEnabled(event.enabled); true }
        is SettingsEvent.SetAutoDownloadCategoryInclude -> {
            downloadPreferences.setAutoDownloadCategoryInclude(event.categoryIds)
            true
        }
        is SettingsEvent.SetAutoDownloadCategoryExclude -> {
            downloadPreferences.setAutoDownloadCategoryExclude(event.categoryIds)
            true
        }
        is SettingsEvent.SetCbzEncryptionEnabled -> {
            if (event.enabled) {
                sendEffect(SettingsEffect.ShowCbzEncryptionPasswordDialog)
            } else {
                downloadPreferences.setCbzEncryptionEnabled(false)
            }
            true
        }
        is SettingsEvent.NavigateToDataUsage -> {
            sendEffect(SettingsEffect.NavigateToDataUsage)
            true
        }
        is SettingsEvent.NavigateToStorageAnalytics -> {
            sendEffect(SettingsEffect.NavigateToStorageAnalytics)
            true
        }
        else -> false
    }
}
