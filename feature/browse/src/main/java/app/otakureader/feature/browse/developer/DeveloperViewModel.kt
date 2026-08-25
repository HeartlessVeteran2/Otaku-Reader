package app.otakureader.feature.browse.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSourceImpl
import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import app.otakureader.core.preferences.DeveloperPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the hidden developer screen.
 *
 * All this does is copy URLs out of `dev-repos.txt` into the same store the visible
 * Browse → Extensions → Repositories screen writes to. It deliberately holds no privileged path:
 * no separate loader, no trust bypass, no blocklist exemption. Everything goes through
 * [ExtensionRepoRepository.addRepository] exactly as a typed URL would, so a repository seeded
 * here behaves identically to one added by hand — including being visible and removable from the
 * ordinary screen by someone who never saw this one.
 *
 * That is a deliberate constraint rather than an accident of implementation. `DeveloperUnlock`
 * explains why: the passphrase gate is obscurity, not a security boundary, so nothing reached
 * through it may be capability a user could not otherwise have.
 */
@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val extensionRepoRepository: ExtensionRepoRepository,
    private val developerPreferences: DeveloperPreferences,
    private val seeds: DeveloperRepoSeeds,
) : ViewModel() {

    private val _state = MutableStateFlow(DeveloperState())
    val state: StateFlow<DeveloperState> = _state.asStateFlow()

    private val _effect = Channel<DeveloperEffect>(Channel.BUFFERED)
    val effect: Flow<DeveloperEffect> = _effect.receiveAsFlow()

    /**
     * Read once, then re-projected against the live repository list.
     *
     * The file is baked into the APK and cannot change while the app runs, so re-reading it on
     * every store emission would be wasted I/O. What *does* change is which entries are already
     * added, which is why [project] recomputes [DeveloperSeed.isAlreadyAdded] each time rather
     * than snapshotting it once.
     */
    private var loadedSeeds: List<String> = emptyList()

    init {
        developerPreferences.isUnlocked
            .onEach { unlocked -> _state.update { it.copy(isUnlocked = unlocked) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            loadedSeeds = seeds.load()
            extensionRepoRepository.getRepositories()
                .onEach(::project)
                .launchIn(viewModelScope)
        }
    }

    private fun project(stored: List<String>) {
        val normalizedStored = stored
            .map(ExtensionRemoteDataSourceImpl::normalizeRepoUrl)
            .toSet()
        _state.update { current ->
            current.copy(
                isLoading = false,
                seeds = loadedSeeds.map { url ->
                    DeveloperSeed(
                        url = url,
                        isAlreadyAdded =
                            ExtensionRemoteDataSourceImpl.normalizeRepoUrl(url) in normalizedStored,
                    )
                },
            )
        }
    }

    fun onEvent(event: DeveloperEvent) {
        when (event) {
            is DeveloperEvent.AddAllSeeds -> addAll()
            is DeveloperEvent.AddSeed -> add(listOf(event.url))
            is DeveloperEvent.Lock -> lock()
        }
    }

    private fun addAll() {
        val pending = _state.value.seeds.filterNot { it.isAlreadyAdded }.map { it.url }
        if (pending.isEmpty()) {
            viewModelScope.launch {
                _effect.send(DeveloperEffect.ShowSnackbar(MESSAGE_NOTHING_PENDING))
            }
            return
        }
        add(pending)
    }

    private fun add(urls: List<String>) {
        viewModelScope.launch {
            var added = 0
            for (url in urls) {
                try {
                    extensionRepoRepository.addRepository(url)
                    added++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep going: one unusable entry in the file should not strand the rest. The
                    // count reported below is what actually landed, not what was attempted.
                }
            }
            val failed = urls.size - added
            _effect.send(DeveloperEffect.ShowSnackbar(summarize(added, failed)))
        }
    }

    private fun summarize(added: Int, failed: Int): String {
        val addedPart = "Added $added ${noun(added)}"
        return if (failed == 0) addedPart else "$addedPart, $failed failed"
    }

    private fun noun(count: Int) = if (count == 1) "repository" else "repositories"

    /**
     * Re-hides the screen.
     *
     * Clears only the unlock flag. Seeded repositories stay: by that point they are ordinary
     * configuration, indistinguishable from hand-added ones, and quietly removing them would be a
     * surprise the user did not ask for.
     */
    private fun lock() {
        viewModelScope.launch {
            developerPreferences.setUnlocked(false)
        }
    }

    private companion object {
        const val MESSAGE_NOTHING_PENDING = "Every seeded repository is already added"
    }
}
