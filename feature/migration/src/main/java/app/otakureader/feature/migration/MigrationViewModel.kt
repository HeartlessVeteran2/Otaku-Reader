package app.otakureader.feature.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationStatus
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.migration.MigrateMangaUseCase
import app.otakureader.domain.usecase.migration.SearchMigrationTargetsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MigrationViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val sourceRepository: SourceRepository,
    private val searchMigrationTargets: SearchMigrationTargetsUseCase,
    private val migrateManga: MigrateMangaUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MigrationState())
    val state: StateFlow<MigrationState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<MigrationEffect>()
    val effect: SharedFlow<MigrationEffect> = _effect.asSharedFlow()

    fun onEvent(event: MigrationEvent) {
        when (event) {
            is MigrationEvent.Initialize -> initialize(event.mangaIds)
            is MigrationEvent.SelectTargetSource -> selectTargetSource(event.sourceId)
            is MigrationEvent.SelectMigrationMode -> selectMigrationMode(event.mode)
            is MigrationEvent.StartMigration -> startMigration()
            is MigrationEvent.SearchForMatches -> searchForMatches(event.mangaId)
            is MigrationEvent.ConfirmMigration -> confirmMigration(event.mangaId, event.candidate)
            is MigrationEvent.SkipManga -> skipManga(event.mangaId)
            is MigrationEvent.DismissError -> dismissError()
            is MigrationEvent.NavigateBack -> navigateBack()
            is MigrationEvent.DismissConfirmationDialog -> dismissConfirmationDialog()
        }
    }

    private fun initialize(mangaIds: List<Long>) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                // Load selected manga
                val manga = mangaRepository.getMangaByIds(mangaIds)

                // Load available sources
                val sourcesFlow = sourceRepository.getSources()
                val sources = sourcesFlow.first().map { source ->
                    SourceItem(
                        id = source.id.toLongOrNull() ?: 0L,
                        name = source.name,
                        lang = source.lang
                    )
                }

                // Initialize migration tasks
                val tasks = manga.map { m ->
                    MigrationTaskItem(
                        manga = m,
                        status = MigrationStatus.PENDING
                    )
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        selectedManga = manga,
                        migrationTasks = tasks,
                        availableSources = sources
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to initialize migration: ${e.message}"
                    )
                }
            }
        }
    }

    private fun selectTargetSource(sourceId: Long) {
        _state.update { it.copy(selectedTargetSourceId = sourceId) }
    }

    private fun selectMigrationMode(mode: MigrationMode) {
        _state.update { it.copy(migrationMode = mode) }
    }

    private fun startMigration() {
        val targetSourceId = _state.value.selectedTargetSourceId
        if (targetSourceId == null) {
            _state.update { it.copy(error = "Please select a target source") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, currentTaskIndex = 0) }

            val tasks = _state.value.migrationTasks.toMutableList()

            tasks.forEachIndexed { index, task ->
                _state.update { it.copy(currentTaskIndex = index) }

                // Update task status to searching
                tasks[index] = task.copy(status = MigrationStatus.SEARCHING)
                _state.update { it.copy(migrationTasks = tasks.toList()) }

                // Search for matches
                val searchResult = searchMigrationTargets(
                    sourceManga = task.manga,
                    targetSourceId = targetSourceId
                )

                if (searchResult.isFailure || searchResult.getOrNull()?.isEmpty() == true) {
                    // No matches found, mark as failed
                    tasks[index] = task.copy(
                        status = MigrationStatus.FAILED,
                        errorMessage = "No matches found in target source"
                    )
                    _state.update { it.copy(migrationTasks = tasks.toList()) }
                    return@forEachIndexed
                }

                val candidates = searchResult.getOrNull() ?: emptyList()
                val bestMatch = candidates.firstOrNull()

                if (bestMatch != null && bestMatch.similarityScore > 0.7f) {
                    // Auto-migrate if high confidence
                    tasks[index] = task.copy(status = MigrationStatus.MIGRATING)
                    _state.update { it.copy(migrationTasks = tasks.toList()) }

                    val migrationResult = migrateManga(
                        sourceManga = task.manga,
                        targetCandidate = bestMatch,
                        mode = _state.value.migrationMode
                    )

                    if (migrationResult.isSuccess) {
                        val result = migrationResult.getOrNull()
                        tasks[index] = task.copy(
                            status = MigrationStatus.COMPLETED,
                            targetCandidate = bestMatch,
                            chaptersMatched = result?.chaptersMatched ?: 0
                        )
                    } else {
                        tasks[index] = task.copy(
                            status = MigrationStatus.FAILED,
                            errorMessage = migrationResult.exceptionOrNull()?.message
                        )
                    }
                } else {
                    // Show confirmation dialog for manual selection
                    tasks[index] = task.copy(status = MigrationStatus.AWAITING_CONFIRMATION)
                    _state.update {
                        it.copy(
                            migrationTasks = tasks.toList(),
                            showConfirmationDialog = true,
                            currentCandidates = candidates
                        )
                    }
                    return@launch // Pause migration for user confirmation
                }

                _state.update { it.copy(migrationTasks = tasks.toList()) }
            }

            _state.update { it.copy(isLoading = false) }

            // Check if all completed
            if (tasks.all { it.status == MigrationStatus.COMPLETED || it.status == MigrationStatus.FAILED || it.status == MigrationStatus.SKIPPED }) {
                viewModelScope.launch {
                    _effect.emit(MigrationEffect.MigrationCompleted)
                }
            }
        }
    }

    private fun searchForMatches(mangaId: Long) {
        val targetSourceId = _state.value.selectedTargetSourceId
        if (targetSourceId == null) {
            _state.update { it.copy(error = "Please select a target source") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }

            val manga = _state.value.selectedManga.find { it.id == mangaId }
            if (manga == null) {
                _state.update { it.copy(isSearching = false, error = "Manga not found") }
                return@launch
            }

            val searchResult = searchMigrationTargets(
                sourceManga = manga,
                targetSourceId = targetSourceId
            )

            if (searchResult.isSuccess) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        currentCandidates = searchResult.getOrNull() ?: emptyList(),
                        showConfirmationDialog = true
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isSearching = false,
                        error = "Search failed: ${searchResult.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    private fun confirmMigration(mangaId: Long, candidate: app.otakureader.domain.model.MigrationCandidate) {
        viewModelScope.launch {
            _state.update { it.copy(showConfirmationDialog = false, isLoading = true) }

            val tasks = _state.value.migrationTasks.toMutableList()
            val taskIndex = tasks.indexOfFirst { it.manga.id == mangaId }

            if (taskIndex == -1) {
                _state.update { it.copy(isLoading = false, error = "Task not found") }
                return@launch
            }

            tasks[taskIndex] = tasks[taskIndex].copy(status = MigrationStatus.MIGRATING)
            _state.update { it.copy(migrationTasks = tasks.toList()) }

            val migrationResult = migrateManga(
                sourceManga = tasks[taskIndex].manga,
                targetCandidate = candidate,
                mode = _state.value.migrationMode
            )

            if (migrationResult.isSuccess) {
                val result = migrationResult.getOrNull()
                tasks[taskIndex] = tasks[taskIndex].copy(
                    status = MigrationStatus.COMPLETED,
                    targetCandidate = candidate,
                    chaptersMatched = result?.chaptersMatched ?: 0
                )
            } else {
                tasks[taskIndex] = tasks[taskIndex].copy(
                    status = MigrationStatus.FAILED,
                    errorMessage = migrationResult.exceptionOrNull()?.message
                )
            }

            _state.update { it.copy(migrationTasks = tasks.toList(), isLoading = false) }

            // Continue with next task if in batch mode
            startMigration()
        }
    }

    private fun skipManga(mangaId: Long) {
        val tasks = _state.value.migrationTasks.toMutableList()
        val taskIndex = tasks.indexOfFirst { it.manga.id == mangaId }

        if (taskIndex != -1) {
            tasks[taskIndex] = tasks[taskIndex].copy(status = MigrationStatus.SKIPPED)
            _state.update { it.copy(migrationTasks = tasks.toList(), showConfirmationDialog = false) }

            // Continue with next task
            startMigration()
        }
    }

    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _effect.emit(MigrationEffect.NavigateBack)
        }
    }

    private fun dismissConfirmationDialog() {
        _state.update { it.copy(showConfirmationDialog = false) }
    }
}
