package app.otakureader.feature.settings.delegate

import app.otakureader.core.preferences.BackupPreferences
import app.otakureader.data.backup.BackupScheduler
import app.otakureader.data.backup.repository.BackupRepository
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSettingsDelegate @Inject constructor(
    private val backupPreferences: BackupPreferences,
    private val backupRepository: BackupRepository,
    private val backupScheduler: BackupScheduler,
) {

    private var updateState: ((SettingsState) -> SettingsState) -> Unit = {}

    fun startObserving(
        scope: CoroutineScope,
        updateState: ((SettingsState) -> SettingsState) -> Unit,
    ) {
        this.updateState = updateState
        scope.launch {
            combine(
                backupPreferences.autoBackupEnabled,
                backupPreferences.autoBackupIntervalHours,
                backupPreferences.autoBackupMaxCount,
            ) { autoBackup, backupInterval, backupMax ->
                updateState { it.copy(
                    autoBackupEnabled = autoBackup,
                    autoBackupIntervalHours = backupInterval,
                    autoBackupMaxCount = backupMax,
                ) }
            }.collect { }
        }
    }

    suspend fun handleEvent(
        event: SettingsEvent,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ): Boolean = when (event) {
        SettingsEvent.OnCreateBackup -> { sendEffect(SettingsEffect.ShowBackupPicker); true }
        SettingsEvent.OnRestoreBackup -> { sendEffect(SettingsEffect.ShowRestorePicker); true }
        is SettingsEvent.CreateBackupWithUri -> {
            updateState { it.copy(isBackupInProgress = true) }
            try {
                backupRepository.createBackup(event.uri)
                sendEffect(SettingsEffect.ShowSnackbar("Backup created successfully"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                sendEffect(SettingsEffect.ShowSnackbar("Failed to create backup: ${e.message}"))
            } finally {
                updateState { it.copy(isBackupInProgress = false) }
            }
            true
        }
        is SettingsEvent.RestoreBackupFromUri -> {
            updateState { it.copy(isRestoreInProgress = true) }
            try {
                backupRepository.restoreBackup(event.uri)
                sendEffect(SettingsEffect.ShowSnackbar("Backup restored successfully"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                sendEffect(SettingsEffect.ShowSnackbar("Failed to restore backup: ${e.message}"))
            } finally {
                updateState { it.copy(isRestoreInProgress = false) }
            }
            true
        }
        is SettingsEvent.SetAutoBackupEnabled -> { handleSetAutoBackupEnabled(event.enabled); true }
        is SettingsEvent.SetAutoBackupInterval -> { handleSetAutoBackupInterval(event.hours); true }
        is SettingsEvent.SetAutoBackupMaxCount -> { backupPreferences.setAutoBackupMaxCount(event.count); true }
        SettingsEvent.RefreshLocalBackups -> {
            val files = backupRepository.listLocalBackups().map { it.name }
            updateState { it.copy(localBackupFiles = files) }
            true
        }
        is SettingsEvent.RestoreLocalBackup -> {
            updateState { it.copy(isRestoreInProgress = true, restoringBackupFileName = event.fileName) }
            try {
                val allFiles = backupRepository.listLocalBackups()
                val file = allFiles.firstOrNull { it.name == event.fileName }
                    ?: throw IllegalArgumentException("Backup file not found: ${event.fileName}")
                backupRepository.restoreLocalBackup(file)
                sendEffect(SettingsEffect.ShowSnackbar("Backup restored successfully"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                sendEffect(SettingsEffect.ShowSnackbar("Failed to restore backup: ${e.message}"))
            } finally {
                updateState { it.copy(isRestoreInProgress = false, restoringBackupFileName = null) }
            }
            true
        }
        else -> false
    }

    private suspend fun handleSetAutoBackupEnabled(enabled: Boolean) {
        backupPreferences.setAutoBackupEnabled(enabled)
        if (enabled) {
            val intervalHours = backupPreferences.autoBackupIntervalHours.first()
            backupScheduler.schedule(intervalHours)
        } else {
            backupScheduler.cancel()
        }
    }

    private suspend fun handleSetAutoBackupInterval(hours: Int) {
        backupPreferences.setAutoBackupIntervalHours(hours)
        if (backupPreferences.autoBackupEnabled.first()) {
            backupScheduler.schedule(hours)
        }
    }
}
