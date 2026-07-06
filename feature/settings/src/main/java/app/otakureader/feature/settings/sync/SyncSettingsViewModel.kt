package app.otakureader.feature.settings.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.SyncSettingsStore
import app.otakureader.domain.repository.SyncRepository
import app.otakureader.domain.scheduler.SyncScheduler
import app.otakureader.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Reader Sync settings screen.
 *
 * Follows the project's MVI pattern:
 * - Exposes immutable [SyncSettingsState] via [uiState].
 * - Accepts [SyncSettingsEvent] intents via [onEvent].
 * - Emits one-shot [SyncSettingsEffect] side-effects via [effect].
 *
 * Uses the domain-layer [SyncScheduler] interface so this ViewModel has no
 * dependency on WorkManager or any data-layer class directly.  This is
 * consistent with how [TrackerSyncScheduler] is used from the Tracking settings.
 */
@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val syncSettingsStore: SyncSettingsStore,
    private val syncRepository: SyncRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncSettingsState())
    val uiState: StateFlow<SyncSettingsState> = _uiState.asStateFlow()

    private val _effect = Channel<SyncSettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        // serverUrl and bearerToken are editable draft fields.  Reading them
        // reactively would overwrite in-progress user edits whenever DataStore
        // re-emits (e.g. after SaveSettings persists the value).  Use a
        // one-shot read instead so the UI draft is only seeded once on start.
        viewModelScope.launch {
            val url = syncSettingsStore.serverUrl.first()
            val token = syncSettingsStore.bearerToken.first()
            _uiState.update { it.copy(serverUrl = url, bearerToken = token) }
        }

        // deviceId and queueSize are read-only display values — observe reactively.
        syncSettingsStore.deviceId
            .onEach { v -> _uiState.update { it.copy(deviceId = v) } }
            .launchIn(viewModelScope)
        syncRepository.observeQueueSize()
            .onEach { v -> _uiState.update { it.copy(queueSize = v) } }
            .launchIn(viewModelScope)

        // Ensure a stable device ID is generated on first launch.
        viewModelScope.launch { syncSettingsStore.ensureDeviceId() }
    }

    fun onEvent(event: SyncSettingsEvent) {
        when (event) {
            is SyncSettingsEvent.SetServerUrl -> {
                _uiState.update { it.copy(serverUrl = event.url) }
            }

            is SyncSettingsEvent.SetBearerToken -> {
                _uiState.update { it.copy(bearerToken = event.token) }
            }

            SyncSettingsEvent.SaveSettings -> viewModelScope.launch {
                val state = _uiState.value
                syncSettingsStore.setServerUrl(state.serverUrl)
                syncSettingsStore.setBearerToken(state.bearerToken)
                _effect.send(SyncSettingsEffect.ShowSnackbar(context.getString(R.string.settings_sync_settings_saved)))
                // Reschedule the periodic worker with the (possibly new) server URL.
                if (state.serverUrl.isNotBlank()) {
                    syncScheduler.schedulePeriodicSync()
                }
            }

            SyncSettingsEvent.SyncNow -> viewModelScope.launch {
                val currentState = _uiState.value
                if (currentState.serverUrl.isBlank()) {
                    _effect.send(SyncSettingsEffect.ShowSnackbar(context.getString(R.string.settings_sync_no_server_configured)))
                    return@launch
                }
                // Save latest drafts before enqueuing so the worker uses current settings.
                syncSettingsStore.setServerUrl(currentState.serverUrl)
                syncSettingsStore.setBearerToken(currentState.bearerToken)
                syncScheduler.enqueueSingleSync()
                _uiState.update { it.copy(lastSyncResult = context.getString(R.string.settings_sync_enqueued)) }
            }

            SyncSettingsEvent.TestConnection -> viewModelScope.launch {
                val url = _uiState.value.serverUrl
                if (url.isBlank()) {
                    _effect.send(SyncSettingsEffect.ShowSnackbar(context.getString(R.string.settings_sync_enter_url_first)))
                    return@launch
                }
                // A real connection test would make a HEAD request; for now we
                // simply report the URL that would be used so the user can verify it.
                _effect.send(SyncSettingsEffect.ShowSnackbar(context.getString(R.string.settings_sync_will_connect_to, url)))
            }
        }
    }
}
