package app.otakureader.feature.settings.delegate

import android.content.Context
import android.net.Uri
import app.otakureader.core.preferences.BackupPreferences
import app.otakureader.domain.backup.BackupRepository
import app.otakureader.domain.backup.BackupScheduler
import app.otakureader.domain.backup.TachiyomiBackupImporter
import app.otakureader.domain.model.BackupOptions
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.FeedRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.OpdsRepository
import app.otakureader.domain.repository.TrackerSyncRepository
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSettingsDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupPreferences: BackupPreferences,
    private val backupRepository: BackupRepository,
    private val backupScheduler: BackupScheduler,
    private val tachiyomiImporter: TachiyomiBackupImporter,
    private val mangaRepository: MangaRepository,
    private val categoryRepository: CategoryRepository,
    private val trackerSyncRepository: TrackerSyncRepository,
    private val opdsRepository: OpdsRepository,
    private val feedRepository: FeedRepository,
) {

    private var updateState: ((SettingsState) -> SettingsState) -> Unit = {}

    /** URI of the backup the user is previewing, awaiting import confirmation. */
    private var pendingImportUri: String? = null

    /**
     * Mirrors of the checklist/preflight dialogs' current option selections. `updateState` only
     * writes to the UI state, so these give `ConfirmCreateBackup`/`RestoreBackupFromUri`/etc. a
     * way to read back what the user picked — same technique as [pendingImportUri] above.
     */
    private var pendingBackupOptions: BackupOptions = BackupOptions.ALL
    private var pendingRestoreOptions: BackupOptions = BackupOptions.ALL

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Failed to read backup file")

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
                backupPreferences.autoBackupLocationUri,
                backupPreferences.lastAutoBackupTimestamp,
            ) { autoBackup, backupInterval, backupMax, locationUri, lastBackup ->
                updateState { it.copy(backup = it.backup.copy(
                    autoBackupEnabled = autoBackup,
                    autoBackupIntervalHours = backupInterval,
                    autoBackupMaxCount = backupMax,
                    autoBackupLocationUri = locationUri,
                    lastAutoBackupTimestamp = lastBackup,
                )) }
            }.collect { }
        }
        scope.launch {
            combine(
                backupPreferences.backupEncryptionEnabled,
                backupPreferences.backupEncryptionPasswordSet,
            ) { encEnabled, encPasswordSet ->
                updateState { it.copy(backup = it.backup.copy(
                    backupEncryptionEnabled = encEnabled,
                    backupEncryptionPasswordSet = encPasswordSet,
                )) }
            }.collect { }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod", "InstanceOfCheckForException")
    suspend fun handleEvent(
        event: SettingsEvent,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ): Boolean = when (event) {
        // ── Pre-backup checklist ──────────────────────────────────────────────────
        // ShowBackupChecklist loads library counts and shows the checklist dialog.
        // The actual file-save picker is opened in ConfirmCreateBackup so the user
        // can review what will be included before committing to the file.
        SettingsEvent.ShowBackupChecklist -> {
            val mangaCount = mangaRepository.getLibraryManga().first().size
            val categoryCount = categoryRepository.getCategories().first().size
            val syncConfigCount = trackerSyncRepository.getSyncConfigurations().first().size
            val opdsCount = opdsRepository.getServers().first().size
            val feedCount = feedRepository.getFeedSources().first().size + feedRepository.getSavedSearches().first().size
            pendingBackupOptions = BackupOptions.ALL
            updateState {
                it.copy(backup = it.backup.copy(
                    showBackupChecklist = true,
                    backupChecklistMangaCount = mangaCount,
                    backupChecklistCategoryCount = categoryCount,
                    backupChecklistSyncConfigCount = syncConfigCount,
                    backupChecklistOpdsCount = opdsCount,
                    backupChecklistFeedCount = feedCount,
                    backupOptions = BackupOptions.ALL,
                ))
            }
            true
        }
        is SettingsEvent.SetBackupOptions -> {
            pendingBackupOptions = event.options
            updateState { it.copy(backup = it.backup.copy(backupOptions = event.options)) }
            true
        }
        SettingsEvent.ConfirmCreateBackup -> {
            if (!pendingBackupOptions.canCreate()) {
                sendEffect(SettingsEffect.ShowSnackbar("Select at least one item to back up"))
            } else {
                // Dismiss checklist, then open the file-save picker.
                updateState { it.copy(backup = it.backup.copy(showBackupChecklist = false)) }
                sendEffect(SettingsEffect.ShowBackupPicker)
            }
            true
        }
        SettingsEvent.DismissBackupChecklist -> {
            updateState { it.copy(backup = it.backup.copy(showBackupChecklist = false)) }
            true
        }

        // ── Pre-restore confirmation ──────────────────────────────────────────────
        // The file picker result routes through ShowRestoreConfirm; ConfirmRestore
        // carries the URI directly so no state-stored side-effect is needed.
        is SettingsEvent.ShowRestoreConfirm -> {
            val fileName = event.uri.lastPathSegment ?: event.uri.toString()
            pendingRestoreOptions = BackupOptions.ALL
            updateState {
                it.copy(backup = it.backup.copy(
                    showRestoreConfirm = true,
                    pendingRestoreUri = event.uri.toString(),
                    pendingRestoreFileName = fileName,
                    restoreOptions = BackupOptions.ALL,
                ))
            }
            true
        }
        is SettingsEvent.SetRestoreOptions -> {
            pendingRestoreOptions = event.options
            updateState { it.copy(backup = it.backup.copy(restoreOptions = event.options)) }
            true
        }
        is SettingsEvent.ConfirmRestore -> {
            // Clear the dialog state and restore using the URI carried in the event.
            // No mutable field needed — the URI is delivered directly by the caller.
            updateState {
                it.copy(backup = it.backup.copy(
                    showRestoreConfirm = false,
                    pendingRestoreUri = null,
                    pendingRestoreFileName = "",
                ))
            }
            handleEvent(SettingsEvent.RestoreBackupFromUri(event.uri), sendEffect)
            true
        }
        SettingsEvent.DismissRestoreConfirm -> {
            updateState {
                it.copy(backup = it.backup.copy(
                    showRestoreConfirm = false,
                    pendingRestoreUri = null,
                    pendingRestoreFileName = "",
                ))
            }
            true
        }

        SettingsEvent.OnCreateBackup -> { sendEffect(SettingsEffect.ShowBackupPicker); true }
        SettingsEvent.OnRestoreBackup -> { sendEffect(SettingsEffect.ShowRestorePicker); true }
        SettingsEvent.OnImportTachiyomiBackup -> { sendEffect(SettingsEffect.ShowTachiyomiImportPicker); true }
        is SettingsEvent.ImportTachiyomiBackupFromUri -> {
            try {
                val data = readBytes(event.uri)
                val preview = tachiyomiImporter.preview(data)
                pendingImportUri = event.uri.toString()
                updateState {
                    it.copy(backup = it.backup.copy(
                        tachiyomiImportPreview = preview,
                        pendingTachiyomiImportUri = event.uri.toString(),
                    ))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendEffect(SettingsEffect.ShowSnackbar("Couldn't read backup: ${e.message}"))
            }
            true
        }
        SettingsEvent.CancelTachiyomiImport -> {
            pendingImportUri = null
            updateState {
                it.copy(backup = it.backup.copy(
                    tachiyomiImportPreview = null,
                    pendingTachiyomiImportUri = null,
                ))
            }
            true
        }
        is SettingsEvent.ConfirmTachiyomiImport -> {
            val uriString = pendingImportUri
            pendingImportUri = null
            if (uriString == null) {
                true
            } else {
                updateState {
                    it.copy(backup = it.backup.copy(
                        isRestoreInProgress = true,
                        isTachiyomiImporting = true,
                        tachiyomiImportPreview = null,
                        pendingTachiyomiImportUri = null,
                        tachiyomiImportProgress = 0,
                        tachiyomiImportTotal = 0,
                    ))
                }
                try {
                    val data = readBytes(Uri.parse(uriString))
                    val result = tachiyomiImporter.importBackup(
                        data = data,
                        overwriteExisting = event.overwriteExisting,
                        onProgress = { current, total ->
                            updateState {
                                it.copy(backup = it.backup.copy(
                                    tachiyomiImportProgress = current,
                                    tachiyomiImportTotal = total,
                                ))
                            }
                        },
                    )
                    sendEffect(SettingsEffect.ShowSnackbar(
                        "Imported ${result.mangaImported} manga, ${result.chaptersImported} chapters, " +
                            "${result.categoriesImported} categories, ${result.trackingImported} tracked " +
                            "(${result.skipped} skipped)"
                    ))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sendEffect(SettingsEffect.ShowSnackbar("Failed to import Tachiyomi backup: ${e.message}"))
                } finally {
                    updateState { it.copy(backup = it.backup.copy(isRestoreInProgress = false, isTachiyomiImporting = false)) }
                }
                true
            }
        }
        is SettingsEvent.CreateBackupWithUri -> {
            if (backupPreferences.backupEncryptionEnabled.first()) {
                sendEffect(SettingsEffect.ShowEncryptionPasswordForBackupDialog(event.uri))
            } else {
                updateState { it.copy(backup = it.backup.copy(isBackupInProgress = true)) }
                try {
                    backupRepository.createBackup(event.uri.toString(), pendingBackupOptions)
                    sendEffect(SettingsEffect.ShowSnackbar("Backup created successfully"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sendEffect(SettingsEffect.ShowSnackbar("Failed to create backup: ${e.message}"))
                } finally {
                    updateState { it.copy(backup = it.backup.copy(isBackupInProgress = false)) }
                }
            }
            true
        }
        is SettingsEvent.CreateEncryptedBackupWithUri -> {
            updateState { it.copy(backup = it.backup.copy(isBackupInProgress = true)) }
            try {
                backupRepository.createEncryptedBackup(event.uri.toString(), event.password.toCharArray(), pendingBackupOptions)
                sendEffect(SettingsEffect.ShowSnackbar("Encrypted backup created successfully"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendEffect(SettingsEffect.ShowSnackbar("Failed to create backup: ${e.message}"))
            } finally {
                updateState { it.copy(backup = it.backup.copy(isBackupInProgress = false)) }
            }
            true
        }
        is SettingsEvent.RestoreBackupFromUri -> {
            val encrypted = try { backupRepository.isBackupEncrypted(event.uri.toString()) } catch (_: Exception) { false }
            if (encrypted) {
                sendEffect(SettingsEffect.ShowEncryptionPasswordForRestoreDialog(event.uri))
            } else {
                updateState {
                    it.copy(backup = it.backup.copy(isRestoreInProgress = true, tachiyomiImportTotal = 0, tachiyomiImportProgress = 0))
                }
                try {
                    backupRepository.restoreBackup(event.uri.toString(), pendingRestoreOptions)
                    sendEffect(SettingsEffect.ShowSnackbar("Backup restored successfully"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sendEffect(SettingsEffect.ShowSnackbar("Failed to restore backup: ${e.message}"))
                } finally {
                    updateState { it.copy(backup = it.backup.copy(isRestoreInProgress = false)) }
                }
            }
            true
        }
        is SettingsEvent.RestoreEncryptedBackupFromUri -> {
            updateState {
                it.copy(backup = it.backup.copy(isRestoreInProgress = true, tachiyomiImportTotal = 0, tachiyomiImportProgress = 0))
            }
            try {
                backupRepository.restoreEncryptedBackup(event.uri.toString(), event.password.toCharArray(), pendingRestoreOptions)
                sendEffect(SettingsEffect.ShowSnackbar("Backup restored successfully"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: javax.crypto.AEADBadTagException) {
                sendEffect(SettingsEffect.ShowSnackbar("Wrong password — could not decrypt backup"))
            } catch (e: Exception) {
                sendEffect(SettingsEffect.ShowSnackbar("Failed to restore backup: ${e.message}"))
            } finally {
                updateState { it.copy(backup = it.backup.copy(isRestoreInProgress = false)) }
            }
            true
        }
        is SettingsEvent.SetAutoBackupEnabled -> { handleSetAutoBackupEnabled(event.enabled); true }
        is SettingsEvent.SetAutoBackupInterval -> { handleSetAutoBackupInterval(event.hours); true }
        is SettingsEvent.SetAutoBackupMaxCount -> { backupPreferences.setAutoBackupMaxCount(event.count); true }
        SettingsEvent.RequestAutoBackupLocationPicker -> { sendEffect(SettingsEffect.ShowAutoBackupLocationPicker); true }
        is SettingsEvent.SetAutoBackupLocation -> { backupPreferences.setAutoBackupLocationUri(event.uri); true }
        SettingsEvent.RefreshLocalBackups -> {
            val files = backupRepository.listLocalBackups().map { it.name }
            updateState { it.copy(backup = it.backup.copy(localBackupFiles = files)) }
            true
        }
        is SettingsEvent.RestoreLocalBackup -> {
            updateState {
                it.copy(
                    backup = it.backup.copy(
                        isRestoreInProgress = true,
                        restoringBackupFileName = event.fileName,
                        tachiyomiImportTotal = 0,
                        tachiyomiImportProgress = 0,
                    )
                )
            }
            try {
                val allFiles = backupRepository.listLocalBackups()
                val file = allFiles.firstOrNull { it.name == event.fileName }
                    ?: throw IllegalArgumentException("Backup file not found: ${event.fileName}")
                backupRepository.restoreLocalBackup(file)
                sendEffect(SettingsEffect.ShowSnackbar("Backup restored successfully"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                sendEffect(SettingsEffect.ShowSnackbar("Failed to restore backup: ${e.message}"))
            } finally {
                updateState { it.copy(backup = it.backup.copy(isRestoreInProgress = false, restoringBackupFileName = null)) }
            }
            true
        }
        is SettingsEvent.SetBackupEncryptionEnabled -> {
            backupPreferences.setBackupEncryptionEnabled(event.enabled)
            true
        }
        SettingsEvent.RequestSetBackupPassword -> {
            sendEffect(SettingsEffect.ShowEncryptionPasswordSetupDialog)
            true
        }
        is SettingsEvent.SetBackupEncryptionPassword -> {
            val bytes = event.password.toByteArray(Charsets.UTF_8)
            val hash  = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            backupPreferences.setBackupEncryptionPasswordHash(hash)
            sendEffect(SettingsEffect.ShowSnackbar("Backup password set"))
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
