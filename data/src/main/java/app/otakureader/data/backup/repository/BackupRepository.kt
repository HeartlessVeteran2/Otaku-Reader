package app.otakureader.data.backup.repository

import android.content.Context
import android.net.Uri
import app.otakureader.data.backup.BackupCreator
import app.otakureader.data.backup.BackupRestorer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing backup and restore operations.
 * Coordinates file I/O with BackupCreator and BackupRestorer.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupCreator: BackupCreator,
    private val backupRestorer: BackupRestorer
) {

    /**
     * Creates a backup and writes it to the provided URI.
     * @param uri Destination URI for the backup file (from SAF picker)
     * @throws Exception if backup creation or write fails
     */
    suspend fun createBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val backupJson = backupCreator.createBackup()

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(backupJson.toByteArray())
        } ?: throw IllegalStateException("Failed to open output stream for URI: $uri")
    }

    /**
     * Restores a backup from the provided URI.
     * @param uri Source URI for the backup file (from SAF picker)
     * @throws Exception if backup read or restore fails
     */
    suspend fun restoreBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val backupJson = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes().decodeToString()
        } ?: throw IllegalStateException("Failed to open input stream for URI: $uri")

        backupRestorer.restoreBackup(backupJson)
    }

    /**
     * Creates an automatic backup and saves it to the app's private backup directory.
     * @return The [File] that was written.
     * @throws Exception if backup creation or write fails.
     */
    suspend fun createLocalBackup(): File = withContext(Dispatchers.IO) {
        val backupJson = backupCreator.createBackup()
        val dir = getLocalBackupDir()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "otakureader_backup_$timestamp.json")
        file.writeText(backupJson)
        file
    }

    /**
     * Restores a backup from a local file path.
     * @param file The local backup [File] to restore from.
     * @throws Exception if restore fails.
     */
    suspend fun restoreLocalBackup(file: File) = withContext(Dispatchers.IO) {
        val backupJson = file.readText()
        backupRestorer.restoreBackup(backupJson)
    }

    /**
     * Returns a list of automatic backup files stored locally, sorted newest first.
     */
    suspend fun listLocalBackups(): List<File> = withContext(Dispatchers.IO) {
        getLocalBackupDir()
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Removes old automatic backup files, keeping only the [maxCount] most recent ones.
     * [maxCount] is coerced to at least 1 so the most recent backup is always retained.
     */
    suspend fun pruneLocalBackups(maxCount: Int) = withContext(Dispatchers.IO) {
        val safeMax = maxCount.coerceAtLeast(1)
        val backups = listLocalBackups()
        if (backups.size > safeMax) {
            backups.drop(safeMax).forEach { it.delete() }
        }
    }

    private fun getLocalBackupDir(): File {
        val dir = File(context.filesDir, LOCAL_BACKUP_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            throw java.io.IOException("Failed to create backup directory: ${dir.absolutePath}")
        }
        return dir
    }

    companion object {
        const val LOCAL_BACKUP_DIR = "auto_backups"
    }
}
