package app.otakureader.feature.migration

import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationStatus

/**
 * MVI State for the Migration screen.
 */
data class MigrationState(
    val isLoading: Boolean = false,
    val selectedManga: List<Manga> = emptyList(),
    val migrationTasks: List<MigrationTaskItem> = emptyList(),
    val availableSources: List<SourceItem> = emptyList(),
    val selectedTargetSourceId: Long? = null,
    val currentTaskIndex: Int = 0,
    val migrationMode: MigrationMode = MigrationMode.MOVE,
    val error: String? = null,
    val isSearching: Boolean = false,
    val showConfirmationDialog: Boolean = false,
    val currentCandidates: List<MigrationCandidate> = emptyList()
)

/**
 * Represents a single migration task in the UI.
 */
data class MigrationTaskItem(
    val manga: Manga,
    val status: MigrationStatus,
    val targetCandidate: MigrationCandidate? = null,
    val chaptersMatched: Int = 0,
    val errorMessage: String? = null
)

/**
 * Represents a source in the source selection UI.
 */
data class SourceItem(
    val id: Long,
    val name: String,
    val lang: String
)

/**
 * Events that can be triggered from the Migration screen.
 */
sealed class MigrationEvent {
    data class Initialize(val mangaIds: List<Long>) : MigrationEvent()
    data class SelectTargetSource(val sourceId: Long) : MigrationEvent()
    data class SelectMigrationMode(val mode: MigrationMode) : MigrationEvent()
    data object StartMigration : MigrationEvent()
    data class SearchForMatches(val mangaId: Long) : MigrationEvent()
    data class ConfirmMigration(val mangaId: Long, val candidate: MigrationCandidate) : MigrationEvent()
    data class SkipManga(val mangaId: Long) : MigrationEvent()
    data object DismissError : MigrationEvent()
    data object NavigateBack : MigrationEvent()
    data object DismissConfirmationDialog : MigrationEvent()
}

/**
 * One-shot effects for the Migration screen.
 */
sealed class MigrationEffect {
    data object NavigateBack : MigrationEffect()
    data class ShowError(val message: String) : MigrationEffect()
    data object MigrationCompleted : MigrationEffect()
}
