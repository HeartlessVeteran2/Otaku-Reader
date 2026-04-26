package app.otakureader.feature.settings.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import app.otakureader.feature.settings.delegate.BackupSettingsDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Focused ViewModel for the Backup & Sync settings section (backup creation/restore, local
 * backup listing, auto-backup schedule).
 *
 * Owns only the [BackupSettingsDelegate] so the backup preferences screen can be unit-tested
 * and navigated to independently of the rest of the settings surface.
 *
 * Note: cloud sync (Google Drive sign-in, sync interval, conflict resolution) currently lives
 * in [app.otakureader.feature.settings.delegate.TrackerSyncSettingsDelegate] and is exposed
 * via [TrackerSettingsViewModel]; that grouping is preserved here for backwards compatibility.
 *
 * Part of the work to split the previously monolithic `SettingsViewModel` into per-section
 * ViewModels aligned with the settings sections.
 */
@HiltViewModel
class BackupSyncViewModel @Inject constructor(
    private val backupDelegate: BackupSettingsDelegate,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        backupDelegate.startObserving(viewModelScope) { reducer -> _state.update(reducer) }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            backupDelegate.handleEvent(event) { _effect.send(it) }
        }
    }

    /**
     * Public shim used by the backup file-picker result callbacks in the screen layer
     * (ActivityResultLauncher callbacks are not routed through [onEvent]).
     */
    fun createBackup(uri: Uri) {
        onEvent(SettingsEvent.CreateBackupWithUri(uri))
    }

    /**
     * Public shim used by the restore file-picker result callbacks in the screen layer
     * (ActivityResultLauncher callbacks are not routed through [onEvent]).
     */
    fun restoreBackup(uri: Uri) {
        onEvent(SettingsEvent.RestoreBackupFromUri(uri))
    }
}
