package app.otakureader.feature.migration

/**
 * MVI contracts for the Migration Entry screen.
 *
 * This screen lets users pick library manga they want to migrate before proceeding
 * to the main [MigrationScreen].
 */
data class MigrationEntryState(
    val isLoading: Boolean = false,
    val mangaList: List<MigrationEntryItem> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val searchQuery: String = "",
    /** Narrows the list to entries whose source no longer resolves. */
    val showOnlyStranded: Boolean = false,
    val error: String? = null
) {
    /**
     * Entries no loaded source can serve.
     *
     * Derived rather than stored so it cannot drift from [mangaList] — the count and the list it
     * describes are always computed from the same snapshot.
     */
    val strandedCount: Int get() = mangaList.count { it.isStranded }
}

data class MigrationEntryItem(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    /**
     * Display name of the source this manga came from, or null when no loaded source owns its key.
     *
     * Null carries real meaning here and is not merely "unknown": the manga row stores a `Long`
     * key that is a one-way hash of a source's string id, so an unresolved key cannot be turned
     * back into a name to show. It means the source is genuinely unreachable — its extension was
     * uninstalled, or the backend that provided it is gone — and every read of this manga will
     * fail until it is migrated to a source that exists.
     */
    val sourceName: String?
) {
    val isStranded: Boolean get() = sourceName == null
}

sealed class MigrationEntryEvent {
    data class OnSearchQueryChange(val query: String) : MigrationEntryEvent()
    data class OnMangaToggle(val mangaId: Long) : MigrationEntryEvent()
    data object SelectAll : MigrationEntryEvent()
    data object SelectAllStranded : MigrationEntryEvent()
    data object ToggleStrandedFilter : MigrationEntryEvent()
    data object ClearSelection : MigrationEntryEvent()
    data object OnStartMigration : MigrationEntryEvent()
    data object NavigateBack : MigrationEntryEvent()
    data object Retry : MigrationEntryEvent()
}

sealed class MigrationEntryEffect {
    data class NavigateToMigration(val selectedMangaIds: List<Long>) : MigrationEntryEffect()
    data object NavigateBack : MigrationEntryEffect()
    data class ShowError(val message: String) : MigrationEntryEffect()
}
