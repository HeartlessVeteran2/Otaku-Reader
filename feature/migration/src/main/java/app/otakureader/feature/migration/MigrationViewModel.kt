package app.otakureader.feature.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationStatus
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.migration.MigrateMangaUseCase
import app.otakureader.domain.usecase.migration.SearchMigrationTargetsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MigrationViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val sourceRepository: SourceRepository,
    private val searchMigrationTargets: SearchMigrationTargetsUseCase,
    private val migrateManga: MigrateMangaUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(MigrationState())
    val state: StateFlow<MigrationState> = _state.asStateFlow()

    private val _effect = Channel<MigrationEffect>(Channel.BUFFERED)
    val effect: Flow<MigrationEffect> = _effect.receiveAsFlow()

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
            is MigrationEvent.RetryFailed -> retryFailed()
            is MigrationEvent.DismissCompletionSummary -> dismissCompletionSummary()
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
            _state.update { it.copy(isLoading = true) }

            val similarityThreshold = appPreferences.migrationSimilarityThreshold.first()
            val alwaysConfirm = appPreferences.migrationAlwaysConfirm.first()
            val minChapterCount = appPreferences.migrationMinChapterCount.first()

            val tasks = _state.value.migrationTasks.toMutableList()

            tasks.forEachIndexed { index, task ->
                // Only process PENDING tasks; skip completed, failed, and skipped
                if (task.status != MigrationStatus.PENDING) return@forEachIndexed

                _state.update { it.copy(currentTaskIndex = index) }

                // Update task status to searching
                tasks[index] = task.copy(
                    status = MigrationStatus.SEARCHING,
                    statusMessage = "Searching for matches..."
                )
                _state.update { it.copy(migrationTasks = tasks) }

                // Search for matches
                val searchResult = searchMigrationTargets(
                    sourceManga = task.manga,
                    targetSourceId = targetSourceId
                )

                if (searchResult.isFailure || searchResult.getOrNull()?.isEmpty() == true) {
                    // No matches found, mark as failed
                    tasks[index] = task.copy(
                        status = MigrationStatus.FAILED,
                        errorMessage = "No matches found in target source",
                        statusMessage = null
                    )
                    _state.update { it.copy(migrationTasks = tasks) }
                    return@forEachIndexed
                }

                val candidates = searchResult.getOrNull() ?: emptyList()

                // Filter by minimum chapter count
                val filteredCandidates = if (minChapterCount > 0) {
                    candidates.filter { it.chapterCount >= minChapterCount }
                } else {
                    candidates
                }

                if (filteredCandidates.isEmpty()) {
                    tasks[index] = task.copy(
                        status = MigrationStatus.FAILED,
                        errorMessage = "No candidates meet the minimum chapter count ($minChapterCount)",
                        statusMessage = null
                    )
                    _state.update { it.copy(migrationTasks = tasks) }
                    return@forEachIndexed
                }

                val bestMatch = filteredCandidates.firstOrNull()

                if (bestMatch != null && bestMatch.similarityScore >= similarityThreshold && !alwaysConfirm) {
                    // Auto-migrate if high confidence and always-confirm is off
                    tasks[index] = task.copy(
                        status = MigrationStatus.MIGRATING,
                        statusMessage = "Migrating data..."
                    )
                    _state.update { it.copy(migrationTasks = tasks) }

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
                            chaptersMatched = result?.chaptersMatched ?: 0,
                            statusMessage = null
                        )
                    } else {
                        tasks[index] = task.copy(
                            status = MigrationStatus.FAILED,
                            errorMessage = migrationResult.exceptionOrNull()?.message,
                            statusMessage = null
                        )
                    }
                } else {
                    // Show confirmation dialog for manual selection
                    tasks[index] = task.copy(
                        status = MigrationStatus.AWAITING_CONFIRMATION,
                        statusMessage = "Awaiting your selection..."
                    )
                    _state.update {
                        it.copy(
                            migrationTasks = tasks,
                            showConfirmationDialog = true,
                            currentCandidates = filteredCandidates
                        )
                    }
                    return@launch // Pause migration for user confirmation
                }

                _state.update { it.copy(migrationTasks = tasks) }
            }

            _state.update { it.copy(isLoading = false) }

            // Show completion summary if all tasks are in a terminal state
            val finalTasks = _state.value.migrationTasks
            val allDone = finalTasks.all {
                it.status == MigrationStatus.COMPLETED ||
                    it.status == MigrationStatus.FAILED ||
                    it.status == MigrationStatus.SKIPPED
            }
            if (allDone) {
                val completedCount = finalTasks.count { it.status == MigrationStatus.COMPLETED }
                val failedCount = finalTasks.count { it.status == MigrationStatus.FAILED }
                val skippedCount = finalTasks.count { it.status == MigrationStatus.SKIPPED }
                _state.update {
                    it.copy(
                        showCompletionSummary = true,
                        completedCount = completedCount,
                        failedCount = failedCount,
                        skippedCount = skippedCount
                    )
                }
                _effect.send(MigrationEffect.MigrationCompleted)
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

            tasks[taskIndex] = tasks[taskIndex].copy(
                status = MigrationStatus.MIGRATING,
                statusMessage = "Migrating data..."
            )
            _state.update { it.copy(migrationTasks = tasks) }

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
                    chaptersMatched = result?.chaptersMatched ?: 0,
                    statusMessage = null
                )
            } else {
                tasks[taskIndex] = tasks[taskIndex].copy(
                    status = MigrationStatus.FAILED,
                    errorMessage = migrationResult.exceptionOrNull()?.message,
                    statusMessage = null
                )
            }

            _state.update { it.copy(migrationTasks = tasks, isLoading = false) }

            // Continue with next PENDING task
            startMigration()
        }
    }

    private fun skipManga(mangaId: Long) {
        val tasks = _state.value.migrationTasks.toMutableList()
        val taskIndex = tasks.indexOfFirst { it.manga.id == mangaId }

        if (taskIndex != -1) {
            tasks[taskIndex] = tasks[taskIndex].copy(
                status = MigrationStatus.SKIPPED,
                statusMessage = null
            )
            _state.update { it.copy(migrationTasks = tasks, showConfirmationDialog = false) }

            // Continue with next PENDING task
            startMigration()
        }
    }

    private fun retryFailed() {
        val tasks = _state.value.migrationTasks.map { task ->
            if (task.status == MigrationStatus.FAILED) {
                task.copy(status = MigrationStatus.PENDING, errorMessage = null, statusMessage = null)
            } else {
                task
            }
        }
        _state.update { it.copy(migrationTasks = tasks, showCompletionSummary = false) }
        startMigration()
    }

    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _effect.send(MigrationEffect.NavigateBack)
        }
    }

    private fun dismissConfirmationDialog() {
        val tasks = _state.value.migrationTasks.toMutableList()
        val awaitingIndex = tasks.indexOfFirst { it.status == MigrationStatus.AWAITING_CONFIRMATION }
        if (awaitingIndex != -1) {
            // Revert the paused task to PENDING so the user can retry later,
            // and clear isLoading so the Start button becomes available again.
            tasks[awaitingIndex] = tasks[awaitingIndex].copy(
                status = MigrationStatus.PENDING,
                statusMessage = null
            )
            _state.update {
                it.copy(
                    showConfirmationDialog = false,
                    migrationTasks = tasks,
                    isLoading = false
                )
            }
        } else {
            _state.update { it.copy(showConfirmationDialog = false) }
        }
    }

    private fun dismissCompletionSummary() {
        _state.update { it.copy(showCompletionSummary = false) }
    }
}
