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
    /**
     * Whether the source inventory has actually loaded.
     *
     * Until it has, nothing can be called stranded: the repository's source list is a StateFlow
     * seeded empty, so the first emission resolves every key to nothing. Treating that as an
     * answer would show the whole library as broken and invite the user to migrate all of it.
     *
     * Held separately from the items rather than inferred from them, because "every entry is
     * stranded" and "sources have not loaded" look identical in the list and want opposite
     * treatment.
     */
    val sourcesKnown: Boolean = false,
    val error: String? = null
) {
    /**
     * Entries no loaded source can serve — zero until the inventory is known.
     *
     * Derived rather than stored so it cannot drift from [mangaList], and gated on
     * [sourcesKnown] so the banner and its actions stay hidden while the answer is still
     * unknown rather than briefly claiming the library is broken.
     */
    val strandedCount: Int get() = if (sourcesKnown) mangaList.count { it.isStranded } else 0
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
