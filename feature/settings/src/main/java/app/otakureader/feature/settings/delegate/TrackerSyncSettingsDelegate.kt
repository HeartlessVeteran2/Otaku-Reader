package app.otakureader.feature.settings.delegate

import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.model.SyncConfiguration
import app.otakureader.domain.repository.TrackerSyncRepository
import app.otakureader.domain.tracking.TrackManager
import app.otakureader.domain.updater.AppUpdateChecker
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import app.otakureader.feature.settings.TrackerInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerSyncSettingsDelegate @Inject constructor(
    private val trackManager: TrackManager,
    private val appUpdateChecker: AppUpdateChecker,
    private val generalPreferences: GeneralPreferences,
    private val trackerSyncRepository: TrackerSyncRepository,
) {

    private var updateState: ((SettingsState) -> SettingsState) -> Unit = {}

    /** Guards against starting a second batch sync while one is already running. */
    private val batchSyncRunning = AtomicBoolean(false)

    fun startObserving(
        scope: CoroutineScope,
        updateState: ((SettingsState) -> SettingsState) -> Unit,
    ) {
        this.updateState = updateState
        refreshTrackers()

        // App update preferences
        scope.launch {
            combine(
                generalPreferences.appUpdateCheckEnabled,
                generalPreferences.lastAppUpdateCheck,
            ) { enabled, lastCheck ->
                updateState { current ->
                    current.copy(
                        appUpdateCheckEnabled = enabled,
                        lastAppUpdateCheck = lastCheck,
                    )
                }
            }.collect { }
        }

        // Per-tracker sync configuration (trackers without a row use the domain default: true)
        scope.launch {
            trackerSyncRepository.getSyncConfigurations().collect { configs ->
                val syncOnRead = configs.associate { it.trackerId to it.syncOnChapterRead }
                updateState { it.copy(tracking = it.tracking.copy(syncOnChapterRead = syncOnRead)) }
            }
        }
    }

    fun refreshTrackers() {
        val trackerInfos = trackManager.all.map { t -> TrackerInfo(t.id, t.name, t.isLoggedIn) }
        updateState { it.copy(tracking = it.tracking.copy(trackers = trackerInfos)) }
    }

    suspend fun handleEvent(
        event: SettingsEvent,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ): Boolean = when (event) {
        is SettingsEvent.LoginTracker -> { loginTracker(event.trackerId, event.username, event.password, sendEffect); true }
        is SettingsEvent.LogoutTracker -> { logoutTracker(event.trackerId, sendEffect); true }
        is SettingsEvent.SetAppUpdateCheckEnabled -> { generalPreferences.setAppUpdateCheckEnabled(event.enabled); true }
        SettingsEvent.CheckForAppUpdate -> { handleCheckForAppUpdate(sendEffect); true }
        SettingsEvent.SyncAllTrackers -> { syncAllTrackers(sendEffect); true }
        SettingsEvent.DismissTrackerSyncSummary -> {
            updateState { it.copy(tracking = it.tracking.copy(batchSyncSummary = null)) }
            true
        }
        is SettingsEvent.SetTrackerSyncOnChapterRead -> {
            setSyncOnChapterRead(event.trackerId, event.enabled)
            true
        }
        else -> false
    }

    /**
     * Persists the per-tracker "sync on chapter read" flag. [TrackerSyncRepository.updateSyncConfiguration]
     * creates the config row with domain defaults when the tracker has none yet.
     */
    private suspend fun setSyncOnChapterRead(trackerId: Int, enabled: Boolean) {
        val existing = trackerSyncRepository.getSyncConfigurations().first()
            .firstOrNull { it.trackerId == trackerId }
            ?: SyncConfiguration(trackerId = trackerId)
        trackerSyncRepository.updateSyncConfiguration(existing.copy(syncOnChapterRead = enabled))
    }

    private suspend fun syncAllTrackers(sendEffect: suspend (SettingsEffect) -> Unit) {
        // Ignore re-taps while a sync is running so we don't fire concurrent batch syncs.
        if (!batchSyncRunning.compareAndSet(false, true)) return
        updateState { it.copy(tracking = it.tracking.copy(batchSyncInProgress = true, batchSyncSummary = null)) }
        try {
            // syncAllPending() only processes manga with pending tracker changes, so untracked
            // manga are skipped, and it staggers calls internally to avoid tracker API bans.
            val summary = trackerSyncRepository.syncAllPending()
            updateState { it.copy(tracking = it.tracking.copy(batchSyncSummary = summary)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sendEffect(SettingsEffect.ShowSnackbar("Tracker sync failed: ${e.message}"))
        } finally {
            batchSyncRunning.set(false)
            updateState { it.copy(tracking = it.tracking.copy(batchSyncInProgress = false)) }
        }
    }

    private suspend fun loginTracker(
        trackerId: Int,
        username: String,
        password: String,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ) {
        updateState { it.copy(tracking = it.tracking.copy(trackingLoginInProgress = true)) }
        try {
            val tracker = trackManager.get(trackerId)
            if (tracker != null) {
                val success = tracker.login(username, password)
                if (success) {
                    refreshTrackers()
                    sendEffect(SettingsEffect.ShowSnackbar("Logged in to ${tracker.name}"))
                } else {
                    sendEffect(SettingsEffect.ShowSnackbar("Failed to login to ${tracker.name}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sendEffect(SettingsEffect.ShowSnackbar("Error: ${e.message}"))
        } finally {
            updateState { it.copy(tracking = it.tracking.copy(trackingLoginInProgress = false)) }
        }
    }

    private suspend fun logoutTracker(trackerId: Int, sendEffect: suspend (SettingsEffect) -> Unit) {
        val tracker = trackManager.get(trackerId)
        tracker?.logout()
        refreshTrackers()
        sendEffect(SettingsEffect.ShowSnackbar("Logged out from ${tracker?.name}"))
    }

    private suspend fun handleCheckForAppUpdate(sendEffect: suspend (SettingsEffect) -> Unit) {
        val versionInfo = appUpdateChecker.checkForUpdate()
        if (versionInfo != null) {
            sendEffect(SettingsEffect.ShowSnackbar("Update available: ${versionInfo.versionName}"))
        } else {
            sendEffect(SettingsEffect.ShowSnackbar("App is up to date"))
        }
    }
}
