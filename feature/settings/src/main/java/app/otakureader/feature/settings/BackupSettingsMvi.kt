@file:Suppress("MatchingDeclarationName")

package app.otakureader.feature.settings

import app.otakureader.domain.model.BackupOptions
import app.otakureader.domain.model.TachiyomiBackupPreview

data class BackupSettingsState(
    val isBackupInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false,
    val restoringBackupFileName: String? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupIntervalHours: Int = 24,
    val autoBackupMaxCount: Int = 5,
    val autoBackupLocationUri: String = "",
    val lastAutoBackupTimestamp: Long = 0L,
    val localBackupFiles: List<String> = emptyList(),
    // Tachiyomi import: preview shown before the user confirms; progress shown during import.
    val tachiyomiImportPreview: TachiyomiBackupPreview? = null,
    val pendingTachiyomiImportUri: String? = null,
    val isTachiyomiImporting: Boolean = false,
    val tachiyomiImportProgress: Int = 0,
    val tachiyomiImportTotal: Int = 0,
    val backupEncryptionEnabled: Boolean = false,
    val backupEncryptionPasswordSet: Boolean = false,
    // Pre-backup checklist dialog
    val showBackupChecklist: Boolean = false,
    val backupChecklistMangaCount: Int = 0,
    val backupChecklistCategoryCount: Int = 0,
    val backupChecklistOpdsCount: Int = 0,
    val backupChecklistFeedCount: Int = 0,
    val backupChecklistSyncConfigCount: Int = 0,
    /** Which data categories the user has selected to include in the next backup. */
    val backupOptions: BackupOptions = BackupOptions.ALL,
    // Pre-restore preflight dialog
    val showRestoreConfirm: Boolean = false,
    val pendingRestoreUri: String? = null,
    val pendingRestoreFileName: String = "",
    /** Which data categories the user has selected to apply from the pending restore. */
    val restoreOptions: BackupOptions = BackupOptions.ALL,
)
