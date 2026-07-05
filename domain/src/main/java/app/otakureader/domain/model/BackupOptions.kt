package app.otakureader.domain.model

/**
 * Which data categories to include when creating a backup, or apply when restoring one.
 * Threaded through [app.otakureader.domain.backup.BackupRepository] at the point of use — this
 * carries no persisted state itself. Default ([ALL]) preserves the pre-#1192-PR-7
 * unconditional backup/restore behavior.
 */
data class BackupOptions(
    val libraryEntries: Boolean = true,
    val chapters: Boolean = true,
    val categories: Boolean = true,
    val tracking: Boolean = true,
    val preferences: Boolean = true,
    val opdsServers: Boolean = true,
    val feed: Boolean = true,
    val syncConfigurations: Boolean = true,
) {
    /** Chapters are per-manga data — meaningless without [libraryEntries]. */
    val effectiveChapters: Boolean get() = libraryEntries && chapters

    /** Tracker sync state is per-manga data — meaningless without [libraryEntries]. */
    val effectiveTracking: Boolean get() = libraryEntries && tracking

    /** At least one section must be selected for a backup to be worth creating. */
    fun canCreate(): Boolean =
        libraryEntries || categories || preferences || opdsServers || feed || syncConfigurations

    companion object {
        val ALL = BackupOptions()
    }
}
