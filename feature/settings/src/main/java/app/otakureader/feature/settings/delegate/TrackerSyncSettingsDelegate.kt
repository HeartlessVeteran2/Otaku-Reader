package app.otakureader.feature.settings.delegate

import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.SyncPreferences
import app.otakureader.data.tracking.TrackManager
import app.otakureader.data.updater.AppUpdateChecker
import app.otakureader.domain.sync.SyncManager
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import app.otakureader.feature.settings.TrackerInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerSyncSettingsDelegate @Inject constructor(
    private val trackManager: TrackManager,
    private val syncPreferences: SyncPreferences,
    private val syncManager: SyncManager,
    private val appUpdateChecker: AppUpdateChecker,
    private val generalPreferences: GeneralPreferences,
) {

    private var updateState: ((SettingsState) -> SettingsState) -> Unit = {}

    fun startObserving(
        scope: CoroutineScope,
        updateState: ((SettingsState) -> SettingsState) -> Unit,
    ) {
        this.updateState = updateState
        refreshTrackers()

        // Sync preferences — combine first 5 flows then zip additional ones
        scope.launch {
            combine(
                syncPreferences.isSyncEnabled,
                syncPreferences.providerId,
                syncPreferences.autoSyncEnabled,
                syncPreferences.syncIntervalHours,
                syncPreferences.syncOnlyOnWifi,
            ) { enabled, providerId, autoSync, interval, wifiOnly ->
                SyncPrefBundle(enabled, providerId, autoSync, interval, wifiOnly)
            }.combine(syncPreferences.conflictResolutionStrategy) { bundle, strategy ->
                val lastSync = if (bundle.enabled) syncManager.getLastSyncTime() else null
                updateState { current ->
                    current.copy(
                        syncEnabled = bundle.enabled,
                        syncProviderId = bundle.providerId,
                        autoSyncEnabled = bundle.autoSync,
                        syncIntervalHours = bundle.interval,
                        syncOnlyOnWifi = bundle.wifiOnly,
                        conflictResolutionStrategy = strategy,
                        lastSyncTime = lastSync,
                    )
                }
            }.collect { }
        }

        // Self-hosted and Google Drive settings
        scope.launch {
            combine(
                syncPreferences.selfHostedServerUrlFlow,
                syncPreferences.selfHostedAuthTokenFlow,
                syncPreferences.googleDriveEmailFlow,
            ) { url, token, driveEmail ->
                Triple(url ?: "", token ?: "", driveEmail)
            }.collect { (url, token, driveEmail) ->
                updateState { current ->
                    current.copy(
                        selfHostedServerUrl = url,
                        selfHostedAuthToken = token,
                        googleDriveAccountEmail = driveEmail,
                    )
                }
            }
        }

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
    }

    fun refreshTrackers() {
        updateState { it.copy(trackers = trackManager.all.map { t -> TrackerInfo(t.id, t.name, t.isLoggedIn) }) }
    }

    suspend fun handleEvent(
        event: SettingsEvent,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ): Boolean = when (event) {
        is SettingsEvent.LoginTracker -> { loginTracker(event.trackerId, event.username, event.password, sendEffect); true }
        is SettingsEvent.LogoutTracker -> { logoutTracker(event.trackerId, sendEffect); true }
        is SettingsEvent.SetSyncEnabled -> { handleSetSyncEnabled(event.enabled, event.providerId, sendEffect); true }
        SettingsEvent.TriggerManualSync -> { syncManager.sync(); true }
        is SettingsEvent.SetAutoSyncEnabled -> { syncPreferences.setAutoSyncEnabled(event.enabled); true }
        is SettingsEvent.SetSyncIntervalHours -> { syncPreferences.setSyncIntervalHours(event.hours); true }
        is SettingsEvent.SetSyncOnlyOnWifi -> { syncPreferences.setSyncOnlyOnWifi(event.onlyWifi); true }
        is SettingsEvent.SetConflictResolutionStrategy -> { syncPreferences.setConflictResolutionStrategy(event.strategy); true }
        is SettingsEvent.SetSelfHostedServerUrl -> { syncPreferences.setSelfHostedServerUrl(event.url); true }
        is SettingsEvent.SetSelfHostedAuthToken -> { syncPreferences.setSelfHostedAuthToken(event.token); true }
        is SettingsEvent.GoogleSignInResult -> { handleGoogleSignInResult(event.email); true }
        SettingsEvent.DisconnectGoogleDrive -> { handleDisconnectGoogleDrive(); true }
        is SettingsEvent.SetAppUpdateCheckEnabled -> { generalPreferences.setAppUpdateCheckEnabled(event.enabled); true }
        SettingsEvent.CheckForAppUpdate -> { handleCheckForAppUpdate(sendEffect); true }
        else -> false
    }

    private suspend fun loginTracker(
        trackerId: Int,
        username: String,
        password: String,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ) {
        updateState { it.copy(trackingLoginInProgress = true) }
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            sendEffect(SettingsEffect.ShowSnackbar("Error: ${e.message}"))
        } finally {
            updateState { it.copy(trackingLoginInProgress = false) }
        }
    }

    private suspend fun logoutTracker(trackerId: Int, sendEffect: suspend (SettingsEffect) -> Unit) {
        val tracker = trackManager.get(trackerId)
        tracker?.logout()
        refreshTrackers()
        sendEffect(SettingsEffect.ShowSnackbar("Logged out from ${tracker?.name}"))
    }

    private suspend fun handleSetSyncEnabled(
        enabled: Boolean,
        providerId: String?,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ) {
        if (enabled && providerId != null) {
            // For Google Drive, launch sign-in if not yet connected
            if (providerId == "google_drive") {
                val currentEmail = syncPreferences.googleDriveEmailFlow.let {
                    // Read current value inline via state
                    null // will be checked below via updateState snapshot
                }
                // We need the current googleDriveAccountEmail from state.
                // Since delegates are @Singleton we can't easily read state here.
                // We use a flag approach: check syncPreferences directly.
                val email = try {
                    // Peek at Google Drive email from preferences directly
                    var result: String? = null
                    syncPreferences.googleDriveEmailFlow.collect { result = it; return@collect }
                    result
                } catch (_: Exception) {
                    null
                }
                if (email.isNullOrBlank()) {
                    sendEffect(SettingsEffect.LaunchGoogleSignIn)
                    return
                }
            }
            syncManager.enableSync(providerId)
        } else {
            syncManager.disableSync()
        }
    }

    private suspend fun handleGoogleSignInResult(email: String) {
        syncPreferences.setGoogleDriveEmail(email)
        updateState { it.copy(googleDriveAccountEmail = email) }
        syncManager.enableSync("google_drive")
    }

    private suspend fun handleDisconnectGoogleDrive() {
        syncPreferences.clearGoogleDriveAccount()
        updateState { it.copy(googleDriveAccountEmail = null) }
        // Disable sync if currently using Google Drive
        val currentProviderId = syncPreferences.providerId.let {
            var result: String? = null
            try {
                it.collect { v -> result = v; return@collect }
            } catch (_: Exception) { }
            result
        }
        if (currentProviderId == "google_drive") {
            syncManager.disableSync()
        }
    }

    private suspend fun handleCheckForAppUpdate(sendEffect: suspend (SettingsEffect) -> Unit) {
        val versionInfo = appUpdateChecker.checkForUpdate()
        if (versionInfo != null) {
            sendEffect(SettingsEffect.ShowSnackbar("Update available: ${versionInfo.versionName}"))
        } else {
            sendEffect(SettingsEffect.ShowSnackbar("App is up to date"))
        }
    }

    /** Intermediate bundle used inside [startObserving] to work around the 5-Flow combine limit. */
    private data class SyncPrefBundle(
        val enabled: Boolean,
        val providerId: String?,
        val autoSync: Boolean,
        val interval: Int,
        val wifiOnly: Boolean,
    )
}
