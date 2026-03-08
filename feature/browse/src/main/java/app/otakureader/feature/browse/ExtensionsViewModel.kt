package app.otakureader.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.installer.ExtensionInstaller
import app.otakureader.domain.repository.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExtensionsState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val installedExtensions: List<Extension> = emptyList(),
    val availableExtensions: List<Extension> = emptyList(),
    val extensionsWithUpdates: List<Extension> = emptyList(),
    val updateCount: Int = 0,
    val repositories: List<String> = emptyList(),
    val activeRepository: String? = null,
    val error: String? = null
) : UiState

sealed interface ExtensionsEvent : UiEvent {
    data object Refresh : ExtensionsEvent
    data class OnSearchQueryChange(val query: String) : ExtensionsEvent
    data class InstallExtension(val extension: Extension) : ExtensionsEvent
    data class UninstallExtension(val extension: Extension) : ExtensionsEvent
    data class UpdateExtension(val extension: Extension) : ExtensionsEvent
    data class ToggleExtensionEnabled(val extension: Extension, val enabled: Boolean) : ExtensionsEvent
    data class AddRepository(val url: String) : ExtensionsEvent
    data class RemoveRepository(val url: String) : ExtensionsEvent
    data class SetActiveRepository(val url: String) : ExtensionsEvent
}

sealed interface ExtensionsEffect : UiEffect {
    data class ShowSnackbar(val message: String) : ExtensionsEffect
    data class ShowError(val message: String) : ExtensionsEffect
}

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val extensionRepository: ExtensionRepository,
    private val extensionInstaller: ExtensionInstaller,
    private val extensionRepoRepository: ExtensionRepoRepository,
    private val sourceRepository: SourceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    private val _state = MutableStateFlow(ExtensionsState())
    val state = combine(
        _state,
        _searchQuery
    ) { state, query ->
        state.copy(
            searchQuery = query,
            // Filter extensions based on search query
            installedExtensions = if (query.isBlank()) {
                state.installedExtensions
            } else {
                state.installedExtensions.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.sources.any { s -> s.name.contains(query, ignoreCase = true) }
                }
            },
            availableExtensions = if (query.isBlank()) {
                state.availableExtensions
            } else {
                state.availableExtensions.filter {
                    it.name.contains(query, ignoreCase = true)
                }
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExtensionsState(),
    )

    private val _effect = Channel<ExtensionsEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        loadExtensions()
        observeRepositories()
        refreshExtensions()
    }

    fun onEvent(event: ExtensionsEvent) {
        when (event) {
            is ExtensionsEvent.Refresh -> refreshExtensions()
            is ExtensionsEvent.OnSearchQueryChange -> _searchQuery.value = event.query
            is ExtensionsEvent.InstallExtension -> installExtension(event.extension)
            is ExtensionsEvent.UninstallExtension -> uninstallExtension(event.extension)
            is ExtensionsEvent.UpdateExtension -> updateExtension(event.extension)
            is ExtensionsEvent.ToggleExtensionEnabled -> toggleExtension(event.extension, event.enabled)
            is ExtensionsEvent.AddRepository -> addRepository(event.url)
            is ExtensionsEvent.RemoveRepository -> removeRepository(event.url)
            is ExtensionsEvent.SetActiveRepository -> setActiveRepository(event.url)
        }
    }

    private fun loadExtensions() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                // Collect installed extensions
                extensionRepository.getInstalledExtensions()
                    .collect { extensions ->
                        _state.update {
                            it.copy(
                                installedExtensions = extensions,
                                isLoading = false
                            )
                        }
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load extensions"
                    )
                }
            }
        }

        viewModelScope.launch {
            try {
                extensionRepository.getAvailableExtensions()
                    .collect { extensions ->
                        _state.update { it.copy(availableExtensions = extensions) }
                    }
            } catch (e: Exception) {
                // Don't show error for available extensions
            }
        }

        viewModelScope.launch {
            try {
                extensionRepository.getExtensionsWithUpdates()
                    .collect { extensions ->
                        _state.update {
                            it.copy(
                                extensionsWithUpdates = extensions,
                                updateCount = extensions.size
                            )
                        }
                    }
            } catch (e: Exception) {
                // Don't show error for updates
            }
        }
    }

    private fun observeRepositories() {
        viewModelScope.launch {
            extensionRepoRepository.getRepositories().collect { repos ->
                _state.update { it.copy(repositories = repos) }
            }
        }

        viewModelScope.launch {
            runCatching { extensionRepoRepository.getActiveRepository() }
                .onSuccess { active ->
                    _state.update { it.copy(activeRepository = active) }
                }
        }
    }

    private fun refreshExtensions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                extensionRepository.refreshAvailableExtensions()
                extensionRepository.checkForUpdates()
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to refresh extensions"
                    )
                }
            }
        }
    }

    private fun toggleExtension(extension: Extension, enabled: Boolean) {
        viewModelScope.launch {
            try {
                extensionRepository.setExtensionEnabled(extension.pkgName, enabled)
                sourceRepository.refreshSources()
            } catch (e: Exception) {
                _effect.send(ExtensionsEffect.ShowError("Failed to update extension: ${e.message}"))
            }
        }
    }

    private fun addRepository(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            runCatching { extensionRepoRepository.addRepository(url.trim()) }
                .onFailure { _effect.send(ExtensionsEffect.ShowError("Invalid repository URL")) }
        }
    }

    private fun removeRepository(url: String) {
        viewModelScope.launch {
            runCatching { extensionRepoRepository.removeRepository(url) }
        }
    }

    private fun setActiveRepository(url: String) {
        viewModelScope.launch {
            runCatching { extensionRepoRepository.setActiveRepository(url) }
                .onSuccess {
                    _state.update { it.copy(activeRepository = url) }
                    refreshExtensions()
                }
        }
    }

    private fun installExtension(extension: Extension) {
        viewModelScope.launch {
            try {
                // Use the installer's download and install method
                val result = extensionInstaller.downloadAndInstall(extension)
                result.onSuccess {
                    _effect.send(ExtensionsEffect.ShowSnackbar("Extension installed: ${extension.name}"))
                    sourceRepository.refreshSources()
                }.onFailure { error ->
                    _effect.send(ExtensionsEffect.ShowError("Failed to install: ${error.message}"))
                }
            } catch (e: Exception) {
                _effect.send(ExtensionsEffect.ShowError("Failed to install: ${e.message}"))
            }
        }
    }

    private fun uninstallExtension(extension: Extension) {
        viewModelScope.launch {
            try {
                val result = extensionInstaller.uninstall(extension.pkgName)
                result.onSuccess {
                    _effect.send(ExtensionsEffect.ShowSnackbar("Extension uninstalled: ${extension.name}"))
                    sourceRepository.refreshSources()
                }.onFailure { error ->
                    _effect.send(ExtensionsEffect.ShowError("Failed to uninstall: ${error.message}"))
                }
            } catch (e: Exception) {
                _effect.send(ExtensionsEffect.ShowError("Failed to uninstall: ${e.message}"))
            }
        }
    }

    private fun updateExtension(extension: Extension) {
        viewModelScope.launch {
            try {
                val apkPath = extension.apkPath
                    ?: throw IllegalStateException("No APK path available")
                extensionRepository.updateExtension(extension.pkgName, apkPath)
                _effect.send(ExtensionsEffect.ShowSnackbar("Extension updated: ${extension.name}"))
                sourceRepository.refreshSources()
            } catch (e: Exception) {
                _effect.send(ExtensionsEffect.ShowError("Failed to update: ${e.message}"))
            }
        }
    }
}
