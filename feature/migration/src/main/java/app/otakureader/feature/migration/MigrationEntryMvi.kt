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
    val error: String? = null
)

data class MigrationEntryItem(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?
)

sealed class MigrationEntryEvent {
    data class OnSearchQueryChange(val query: String) : MigrationEntryEvent()
    data class OnMangaToggle(val mangaId: Long) : MigrationEntryEvent()
    data object SelectAll : MigrationEntryEvent()
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
