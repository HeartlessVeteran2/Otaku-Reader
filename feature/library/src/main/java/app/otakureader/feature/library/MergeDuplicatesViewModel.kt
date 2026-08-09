package app.otakureader.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.associateBySourceKey
import app.otakureader.domain.usecase.FillMissingChaptersUseCase
import app.otakureader.domain.usecase.LinkAlternativeSourceUseCase
import app.otakureader.domain.usecase.MergeDuplicateMangaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MergeDuplicatesViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val sourceRepository: SourceRepository,
    private val mergeDuplicateManga: MergeDuplicateMangaUseCase,
    private val linkAlternativeSource: LinkAlternativeSourceUseCase,
    private val fillMissingChapters: FillMissingChaptersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MergeDuplicatesState())
    val state: StateFlow<MergeDuplicatesState> = _state.asStateFlow()

    private val _effect = Channel<MergeDuplicatesEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        observeDuplicates()
        observeSourceNames()
    }

    fun onEvent(event: MergeDuplicatesEvent) {
        when (event) {
            is MergeDuplicatesEvent.LoadDuplicates -> observeDuplicates()
            is MergeDuplicatesEvent.SelectPrimary -> selectPrimary(event.groupIndex, event.primaryId)
            is MergeDuplicatesEvent.MergeGroup -> mergeGroup(event.group)
            is MergeDuplicatesEvent.MergeAll -> mergeAll()
            is MergeDuplicatesEvent.LinkAsAlternative -> linkAsAlternative(event.primaryId, event.altId)
            is MergeDuplicatesEvent.FillMissingChapters -> fillMissing(event.primaryId, event.altId)
            is MergeDuplicatesEvent.UnlinkAlternative -> unlinkAlternative(event.primaryId, event.altId)
        }
    }

    private fun observeDuplicates() {
        mangaRepository.findDuplicates()
            .onEach { duplicateLists ->
                val newGroups = duplicateLists.map { entries ->
                    val existing = _state.value.duplicateGroups.firstOrNull { g ->
                        g.entries.map { it.id }.toSet() == entries.map { it.id }.toSet()
                    }
                    existing?.copy(entries = entries) ?: DuplicateGroup(entries)
                }
                // Hydrate linkedAlternatives for every manga ID currently on screen
                val allIds = newGroups.flatMap { g -> g.entries.map { it.id } }.distinct()
                val linked = allIds.associate { id ->
                    id to mangaRepository.getAlternativeSourceIds(id).toSet()
                }
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        duplicateGroups = newGroups,
                        linkedAlternatives = linked,
                        error = null,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSourceNames() {
        sourceRepository.getSources()
            .onEach { sources ->
                // Indexed by every key a manga row can hold, matching getSourceByKey. This used
                // to parse the id as a number and only fall back to hashing when that failed, so
                // numeric (APK) sources were keyed one way and everything else another — and the
                // numeric ones, which are most of them, never matched a manga row at all.
                val names = sources.associateBySourceKey { it.id }.mapValues { (_, s) -> s.name }
                _state.update { it.copy(sourceNames = names) }
            }
            .launchIn(viewModelScope)
    }

    private fun selectPrimary(groupIndex: Int, primaryId: Long) {
        _state.update { current ->
            val updated = current.duplicateGroups.toMutableList()
            if (groupIndex in updated.indices) {
                updated[groupIndex] = updated[groupIndex].copy(primaryId = primaryId)
            }
            current.copy(duplicateGroups = updated)
        }
    }

    private fun mergeGroup(group: DuplicateGroup) {
        viewModelScope.launch {
            _state.update { it.copy(isMerging = true) }
            try {
                val secondaryIds = group.entries.map { it.id }.filter { it != group.primaryId }
                mergeDuplicateManga(group.primaryId, secondaryIds)
                _effect.send(MergeDuplicatesEffect.ShowSnackbar("Merged ${secondaryIds.size + 1} entries into one"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(MergeDuplicatesEffect.ShowSnackbar("Merge failed: ${e.message}"))
            } finally {
                _state.update { it.copy(isMerging = false) }
            }
        }
    }

    private fun mergeAll() {
        val groups = _state.value.duplicateGroups
        if (groups.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isMerging = true) }
            var mergedCount = 0
            var failCount = 0
            for (group in groups) {
                try {
                    val secondaryIds = group.entries.map { it.id }.filter { it != group.primaryId }
                    mergeDuplicateManga(group.primaryId, secondaryIds)
                    mergedCount++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failCount++
                }
            }
            _state.update { it.copy(isMerging = false) }
            val msg = if (failCount == 0) {
                "Merged $mergedCount duplicate group(s)"
            } else {
                "Merged $mergedCount group(s), $failCount failed"
            }
            _effect.send(MergeDuplicatesEffect.ShowSnackbar(msg))
            if (_state.value.duplicateGroups.isEmpty()) {
                _effect.send(MergeDuplicatesEffect.NavigateBack)
            }
        }
    }

    private fun linkAsAlternative(primaryId: Long, altId: Long) {
        viewModelScope.launch {
            try {
                linkAlternativeSource(primaryId, altId)
                updateLinkedAlternatives(primaryId, altId)
                _effect.send(MergeDuplicatesEffect.ShowSnackbar("Linked as alternative sources"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(MergeDuplicatesEffect.ShowSnackbar("Link failed: ${e.message}"))
            }
        }
    }

    private fun fillMissing(primaryId: Long, altId: Long) {
        viewModelScope.launch {
            try {
                val added = fillMissingChapters(primaryId, altId)
                val msg = if (added == 0) "No missing chapters found" else "Added $added missing chapter(s)"
                _effect.send(MergeDuplicatesEffect.ShowSnackbar(msg))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(MergeDuplicatesEffect.ShowSnackbar("Fill failed: ${e.message}"))
            }
        }
    }

    private fun unlinkAlternative(primaryId: Long, altId: Long) {
        viewModelScope.launch {
            try {
                mangaRepository.unlinkAlternativeSource(primaryId, altId)
                updateLinkedAlternatives(primaryId, altId)
                _effect.send(MergeDuplicatesEffect.ShowSnackbar("Alternative link removed"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(MergeDuplicatesEffect.ShowSnackbar("Unlink failed: ${e.message}"))
            }
        }
    }

    /** Fetches fresh link state from the DB then applies it atomically to avoid race conditions. */
    private suspend fun updateLinkedAlternatives(primaryId: Long, altId: Long) {
        val primaryAlts = mangaRepository.getAlternativeSourceIds(primaryId).toSet()
        val altAlts = mangaRepository.getAlternativeSourceIds(altId).toSet()
        _state.update { current ->
            val updated = current.linkedAlternatives.toMutableMap()
            updated[primaryId] = primaryAlts
            updated[altId] = altAlts
            current.copy(linkedAlternatives = updated)
        }
    }
}
