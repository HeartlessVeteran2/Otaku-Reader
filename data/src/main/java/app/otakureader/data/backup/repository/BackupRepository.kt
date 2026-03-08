package app.otakureader.data.backup.repository

import android.content.Context
import android.net.Uri
import app.otakureader.data.backup.BackupCreator
import app.otakureader.data.backup.BackupRestorer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
}
