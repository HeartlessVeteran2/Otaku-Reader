package app.otakureader.domain.scheduler

/**
 * Schedules the one-time migration that renames download source folders from a numeric
 * sourceId to the source's display name. Safe to call on every app start — the underlying
 * work is a no-op once it has already completed.
 */
interface DownloadFolderMigrationScheduler {
    fun schedule()
}
