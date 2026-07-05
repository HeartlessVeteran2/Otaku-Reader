package app.otakureader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.NotificationPreferences
import app.otakureader.domain.scheduler.ExtensionUpdateScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationSettingsState(
    val smartBatchingEnabled: Boolean = true,
    val perMangaCooldownMinutes: Int = 60,
    val maxIndividualNotifications: Int = 3,
    val respectQuietHours: Boolean = true,
    val hideNotificationContent: Boolean = false,
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 8,
    val extensionAutoUpdateEnabled: Boolean = false,
    val extensionAutoUpdateWifiOnly: Boolean = true,
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val prefs: NotificationPreferences,
    private val generalPreferences: GeneralPreferences,
    private val extensionUpdateScheduler: ExtensionUpdateScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationSettingsState())
    val state: StateFlow<NotificationSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.smartBatchingEnabled,
                prefs.perMangaCooldownMinutes,
                prefs.maxIndividualNotifications,
                prefs.respectQuietHours,
                prefs.hideNotificationContent,
            ) { batching, cooldown, max, quiet, hideContent ->
                _state.update {
                    it.copy(
                        smartBatchingEnabled = batching,
                        perMangaCooldownMinutes = cooldown,
                        maxIndividualNotifications = max,
                        respectQuietHours = quiet,
                        hideNotificationContent = hideContent,
                    )
                }
            }.collect { }
        }
        viewModelScope.launch {
            combine(prefs.quietHoursStart, prefs.quietHoursEnd) { start, end ->
                _state.update { it.copy(quietHoursStart = start, quietHoursEnd = end) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                generalPreferences.extensionAutoUpdateEnabled,
                generalPreferences.extensionAutoUpdateWifiOnly,
            ) { enabled, wifiOnly ->
                _state.update {
                    it.copy(extensionAutoUpdateEnabled = enabled, extensionAutoUpdateWifiOnly = wifiOnly)
                }
            }.collect { }
        }
    }

    fun setExtensionAutoUpdate(enabled: Boolean) = viewModelScope.launch {
        generalPreferences.setExtensionAutoUpdateEnabled(enabled)
        // Reschedule immediately so the toggle takes effect without waiting for the next app start.
        if (enabled) {
            extensionUpdateScheduler.schedule(
                intervalHours = generalPreferences.extensionAutoUpdateIntervalHours.first(),
                wifiOnly = generalPreferences.extensionAutoUpdateWifiOnly.first(),
            )
        } else {
            extensionUpdateScheduler.cancel()
        }
    }

    fun setExtensionAutoUpdateWifiOnly(enabled: Boolean) = viewModelScope.launch {
        generalPreferences.setExtensionAutoUpdateWifiOnly(enabled)
        // Re-apply the network constraint live if auto-update is currently on.
        if (generalPreferences.extensionAutoUpdateEnabled.first()) {
            extensionUpdateScheduler.schedule(
                intervalHours = generalPreferences.extensionAutoUpdateIntervalHours.first(),
                wifiOnly = enabled,
            )
        }
    }

    fun setSmartBatching(enabled: Boolean) = viewModelScope.launch { prefs.setSmartBatching(enabled) }
    fun setCooldown(minutes: Int) = viewModelScope.launch { prefs.setPerMangaCooldownMinutes(minutes) }
    fun setMaxIndividual(count: Int) = viewModelScope.launch { prefs.setMaxIndividualNotifications(count) }
    fun setRespectQuietHours(enabled: Boolean) = viewModelScope.launch { prefs.setRespectQuietHours(enabled) }
    fun setHideNotificationContent(enabled: Boolean) = viewModelScope.launch { prefs.setHideNotificationContent(enabled) }

    fun setQuietHoursStart(hour: Int) = viewModelScope.launch {
        // Read the other bound from already-collected state instead of re-suspending on
        // a DataStore Flow. Parallel .first() reads in two setters can interleave with the
        // writes and clobber rapid start/end changes; state is always synced with prefs.
        prefs.setQuietHours(hour, _state.value.quietHoursEnd)
    }

    fun setQuietHoursEnd(hour: Int) = viewModelScope.launch {
        prefs.setQuietHours(_state.value.quietHoursStart, hour)
    }
}
