package app.otakureader.feature.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.model.DownloadBlockedException
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveDownloadFolderName
import app.otakureader.domain.scheduler.LibraryUpdateScheduler
import app.otakureader.domain.usecase.GetLastUpdateRunSummaryUseCase
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.GetRecentUpdatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getRecentUpdatesUseCase: GetRecentUpdatesUseCase,
    private val getLibraryMangaUseCase: GetLibraryMangaUseCase,
    private val getLastUpdateRunSummaryUseCase: GetLastUpdateRunSummaryUseCase,
    private val generalPreferences: GeneralPreferences,
    private val downloadRepository: DownloadRepository,
    private val chapterRepository: ChapterRepository,
    private val libraryUpdateScheduler: LibraryUpdateScheduler,
    private val sourceRepository: SourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatesState())
    val state: StateFlow<UpdatesState> = _state.asStateFlow()

    private val _effect = Channel<UpdatesEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        loadUpdates()
        loadLastRunSummary()
        markUpdatesViewed()
        observeActiveDownloads()
    }

    @Suppress("CyclomaticComplexMethod")
    fun onEvent(event: UpdatesEvent) {
        when (event) {
            UpdatesEvent.Refresh -> loadUpdates()
            is UpdatesEvent.OnChapterClick -> handleChapterClick(event.mangaId, event.chapterId)
            is UpdatesEvent.OnChapterLongClick -> toggleSelection(event.chapterId)
            is UpdatesEvent.OnDownloadChapter -> downloadChapter(event.mangaId, event.chapterId)
            UpdatesEvent.ClearSelection -> _state.update { it.copy(selectedItems = emptySet()) }
            UpdatesEvent.SelectAll -> selectAll()
            UpdatesEvent.InvertSelection -> invertSelection()
            UpdatesEvent.DownloadSelected -> downloadSelected()
            UpdatesEvent.MarkSelectedAsRead -> markSelectedAsRead()
            UpdatesEvent.MarkSelectedAsUnread -> markSelectedAsUnread()
            is UpdatesEvent.MarkChapterAsRead -> markSingleChapterAsRead(event.chapterId)
            is UpdatesEvent.UnmarkChapterAsRead -> unmarkChapterAsRead(event.chapterId)
            is UpdatesEvent.UnmarkSelectedAsRead -> unmarkSelectedAsRead(event.chapterIds)
            UpdatesEvent.StartLibraryUpdate -> startLibraryUpdate()
            is UpdatesEvent.SetDateFilter -> _state.update {
                it.copy(dateFilterStart = event.start, dateFilterEnd = event.end)
            }
            UpdatesEvent.ClearDateFilter -> _state.update {
                it.copy(dateFilterStart = null, dateFilterEnd = null)
            }
            UpdatesEvent.ToggleDisplayMode -> _state.update { state ->
                val next = if (state.displayMode == UpdatesDisplayMode.GROUPED_BY_MANGA) {
                    UpdatesDisplayMode.GROUPED_BY_DATE
                } else {
                    UpdatesDisplayMode.GROUPED_BY_MANGA
                }
                state.copy(displayMode = next)
            }
            UpdatesEvent.ShowUpdateErrors,
            UpdatesEvent.HideUpdateErrors,
            UpdatesEvent.ClearAllUpdateErrors,
            UpdatesEvent.ShowPendingUpdates,
            UpdatesEvent.HidePendingUpdates -> handleOverlayEvent(event)
            is UpdatesEvent.ClearUpdateError -> handleOverlayEvent(event)
            is UpdatesEvent.ToggleMangaGroupExpansion -> _state.update { state ->
                val expanded = state.expandedMangaGroups
                state.copy(
                    expandedMangaGroups = if (event.mangaId in expanded) expanded - event.mangaId
                                         else expanded + event.mangaId,
                )
            }
            is UpdatesEvent.ToggleDateMangaGroup -> _state.update { state ->
                val exp = state.expandedDateMangaGroups
                state.copy(
                    expandedDateMangaGroups = if (event.key in exp) exp - event.key else exp + event.key,
                )
            }
            UpdatesEvent.ShowFilterDialog -> _state.update { it.copy(showFilterDialog = true) }
            UpdatesEvent.HideFilterDialog -> _state.update { it.copy(showFilterDialog = false) }
        }
    }

    private fun handleOverlayEvent(event: UpdatesEvent) {
        when (event) {
            UpdatesEvent.ShowUpdateErrors -> _state.update { it.copy(showUpdateErrors = true) }
            UpdatesEvent.HideUpdateErrors -> _state.update { it.copy(showUpdateErrors = false) }
            is UpdatesEvent.ClearUpdateError -> _state.update { state ->
                state.copy(updateErrors = state.updateErrors.filter { it.mangaId != event.mangaId })
            }
            UpdatesEvent.ClearAllUpdateErrors -> _state.update { it.copy(updateErrors = emptyList()) }
            UpdatesEvent.ShowPendingUpdates -> {
                _state.update { it.copy(showPendingUpdates = true) }
                loadPendingUpdates()
            }
            UpdatesEvent.HidePendingUpdates -> _state.update { it.copy(showPendingUpdates = false) }
            else -> Unit
        }
    }

    private fun handleChapterClick(mangaId: Long, chapterId: Long) {
        if (_state.value.selectedItems.isNotEmpty()) {
            toggleSelection(chapterId)
        } else {
            viewModelScope.launch {
                _effect.send(UpdatesEffect.NavigateToReader(mangaId, chapterId))
            }
        }
    }

    private fun toggleSelection(chapterId: Long) {
        _state.update { state ->
            val sel = state.selectedItems
            state.copy(
                selectedItems = if (chapterId in sel) sel - chapterId else sel + chapterId
            )
        }
    }

    /**
     * IDs of the updates currently visible to the user — i.e. those passing the active date
     * filter (the same filter the screen applies before rendering). Selection-building actions
     * must restrict to these so they never select date-hidden entries.
     */
    private fun UpdatesState.visibleUpdateIds(): Set<Long> {
        val start = dateFilterStart
        val end = dateFilterEnd
        return updates.filter { update ->
            val date = update.chapter.dateFetch
            (start == null || date >= start) && (end == null || date <= end)
        }.map { it.chapter.id }.toSet()
    }

    private fun selectAll() {
        _state.update { state ->
            // Add only the visible updates; keep any already-selected hidden entries intact.
            state.copy(selectedItems = state.selectedItems + state.visibleUpdateIds())
        }
    }

    /**
     * Selects every visible update not currently selected and deselects the visible ones that are
     * (Mihon/Komikku parity). Restricted to the date-filtered set so a filter can't flip the
     * selection state of hidden updates; already-selected hidden entries are preserved.
     */
    private fun invertSelection() {
        _state.update { state ->
            val visibleIds = state.visibleUpdateIds()
            val keptHidden = state.selectedItems - visibleIds
            state.copy(selectedItems = keptHidden + (visibleIds - state.selectedItems))
        }
    }

    private fun downloadChapter(mangaId: Long, chapterId: Long) {
        val update = _state.value.updates.find { it.chapter.id == chapterId } ?: return
        viewModelScope.launch {
            runCatching {
                downloadRepository.enqueueChapter(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    mangaTitle = update.manga.title,
                    chapterTitle = update.chapter.name,
                    sourceName = sourceRepository.resolveDownloadFolderName(update.manga.sourceId)
                )
            }.onSuccess {
                _effect.send(UpdatesEffect.ShowSnackbar(
                    context.getString(R.string.updates_download_queued, update.chapter.name)
                ))
            }.onFailure { e ->
                val message = if (e is DownloadBlockedException) {
                    context.getString(R.string.updates_download_blocked_data_saver)
                } else {
                    context.getString(R.string.updates_download_failed, update.chapter.name)
                }
                _effect.send(UpdatesEffect.ShowSnackbar(message))
            }
        }
    }

    private fun downloadSelected() {
        val selected = _state.value.selectedItems
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val updates = _state.value.updates.filter { it.chapter.id in selected }
            var successCount = 0
            var failCount = 0
            var blockedByDataSaver = false
            updates.forEach { update ->
                runCatching {
                    downloadRepository.enqueueChapter(
                        mangaId = update.manga.id,
                        chapterId = update.chapter.id,
                        mangaTitle = update.manga.title,
                        chapterTitle = update.chapter.name,
                        sourceName = sourceRepository.resolveDownloadFolderName(update.manga.sourceId)
                    )
                }.onSuccess { successCount++ }.onFailure { e ->
                    if (e is DownloadBlockedException) blockedByDataSaver = true
                    failCount++
                }
            }
            _state.update { it.copy(selectedItems = emptySet()) }
            val message = when {
                blockedByDataSaver -> context.getString(R.string.updates_bulk_download_data_saver_blocked)
                failCount == 0 -> context.getString(R.string.updates_bulk_download_queued, successCount)
                else -> context.getString(R.string.updates_bulk_download_partial, successCount, failCount)
            }
            _effect.send(UpdatesEffect.ShowSnackbar(message))
        }
    }

    private fun markSelectedAsRead() {
        val selected = _state.value.selectedItems
        if (selected.isEmpty()) return
        val count = selected.size
        viewModelScope.launch {
            try {
                chapterRepository.updateChapterProgress(selected, read = true, lastPageRead = UNREAD_LAST_PAGE_READ)
                _state.update { it.copy(selectedItems = it.selectedItems - selected) }
                _effect.send(
                    UpdatesEffect.ShowUndoBulkReadSnackbar(
                        message = context.resources.getQuantityString(R.plurals.updates_marked_as_read, count, count),
                        chapterIds = selected,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(UpdatesEffect.ShowSnackbar(context.getString(R.string.updates_mark_as_read_failed)))
            }
        }
    }

    /**
     * Marks the selected chapters as unread (Mihon/Komikku parity). Non-destructive — the user
     * can simply mark them read again — so a plain confirmation snackbar is shown rather than an
     * undo action (unlike the destructive mark-as-read flow).
     */
    private fun markSelectedAsUnread() {
        val selected = _state.value.selectedItems
        if (selected.isEmpty()) return
        val count = selected.size
        viewModelScope.launch {
            try {
                chapterRepository.updateChapterProgress(selected, read = false, lastPageRead = UNREAD_LAST_PAGE_READ)
                _state.update { it.copy(selectedItems = it.selectedItems - selected) }
                _effect.send(
                    UpdatesEffect.ShowSnackbar(
                        context.resources.getQuantityString(R.plurals.updates_marked_as_unread, count, count)
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(UpdatesEffect.ShowSnackbar(context.getString(R.string.updates_mark_as_unread_failed)))
            }
        }
    }

    private fun unmarkSelectedAsRead(chapterIds: Set<Long>) {
        viewModelScope.launch {
            try {
                chapterRepository.updateChapterProgress(chapterIds, read = false, lastPageRead = UNREAD_LAST_PAGE_READ)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(UpdatesEffect.ShowSnackbar(context.getString(R.string.updates_unmark_as_read_failed)))
            }
        }
    }

    private fun markSingleChapterAsRead(chapterId: Long) {
        viewModelScope.launch {
            try {
                chapterRepository.updateChapterProgress(setOf(chapterId), read = true, lastPageRead = UNREAD_LAST_PAGE_READ)
                _effect.send(
                    UpdatesEffect.ShowUndoSnackbar(
                        message = context.getString(R.string.updates_marked_as_read_single),
                        chapterId = chapterId,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(UpdatesEffect.ShowSnackbar(context.getString(R.string.updates_mark_as_read_failed)))
            }
        }
    }

    private fun unmarkChapterAsRead(chapterId: Long) {
        viewModelScope.launch {
            try {
                chapterRepository.updateChapterProgress(setOf(chapterId), read = false, lastPageRead = UNREAD_LAST_PAGE_READ)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(UpdatesEffect.ShowSnackbar(context.getString(R.string.updates_unmark_as_read_failed)))
            }
        }
    }

    private fun loadUpdates() {
        _state.update { it.copy(isLoading = true, error = null) }
        getRecentUpdatesUseCase()
            .onEach { updates ->
                // Build the manga-grouped view. Group by manga ID, preserve ordering from
                // the flat list (newest chapter first), then sort groups so the one with
                // the most-recent chapter comes first.
                val groups = updates
                    .groupBy { it.manga.id }
                    .values
                    .map { chapterList ->
                        MangaUpdateGroup(
                            manga = chapterList.first().manga,
                            chapters = chapterList,
                        )
                    }
                    .sortedByDescending { group ->
                        group.chapters.maxOf { it.chapter.dateFetch }
                    }
                _state.update { it.copy(isLoading = false, updates = updates, groupedByManga = groups) }
            }
            .catch { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
            .launchIn(viewModelScope)
    }

    /** Observe the last library update run summary for the diagnostics card. */
    private fun loadLastRunSummary() {
        getLastUpdateRunSummaryUseCase()
            .onEach { summary ->
                _state.update { it.copy(lastRunSummary = summary) }
            }
            .catch { /* diagnostics failure should not affect the main updates list */ }
            .launchIn(viewModelScope)
    }

    /** Observe the download queue and surface active/queued items for per-row progress UI. */
    private fun observeActiveDownloads() {
        downloadRepository.observeDownloads()
            .onEach { items ->
                _state.update { it.copy(activeDownloads = items.filter { d -> d.isActive }.associateBy { d -> d.chapterId }) }
            }
            .catch { /* download progress failure should not affect the main updates list */ }
            .launchIn(viewModelScope)
    }

    /** Record the current time so the Library badge counter resets to zero. */
    private fun markUpdatesViewed() {
        viewModelScope.launch {
            generalPreferences.setLastUpdatesViewedAt(System.currentTimeMillis())
        }
    }

    /** Load manga that will be checked during the next library update. */
    private fun loadPendingUpdates() {
        viewModelScope.launch {
            try {
                val libraryManga = getLibraryMangaUseCase().first()
                val pendingManga = libraryManga.map { manga ->
                    PendingUpdateManga(
                        mangaId = manga.id,
                        title = manga.title,
                        thumbnailUrl = manga.thumbnailUrl,
                        sourceName = sourceRepository.resolveDownloadFolderName(manga.sourceId),
                        lastChecked = 0L
                    )
                }
                _state.update { state -> state.copy(pendingUpdates = pendingManga) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { state -> state.copy(pendingUpdates = emptyList()) }
            }
        }
    }

    /** Start a manual library update; shows a brief pull-to-refresh indicator. */
    private fun startLibraryUpdate() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, showPendingUpdates = false) }
        viewModelScope.launch {
            try {
                libraryUpdateScheduler.enqueueNow()
                _effect.send(UpdatesEffect.ShowSnackbar(context.getString(R.string.updates_library_update_started)))
                delay(REFRESH_INDICATOR_DURATION_MS)
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    companion object {
        const val REFRESH_INDICATOR_DURATION_MS = 1_500L
        private const val UNREAD_LAST_PAGE_READ = 0
    }
}
