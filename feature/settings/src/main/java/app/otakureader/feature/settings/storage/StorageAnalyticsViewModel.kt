package app.otakureader.feature.settings.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class StorageAnalyticsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageAnalyticsState())
    val state: StateFlow<StorageAnalyticsState> = _state.asStateFlow()

    private val _effects = Channel<StorageAnalyticsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init { load() }

    fun onEvent(event: StorageAnalyticsEvent) {
        when (event) {
            is StorageAnalyticsEvent.ToggleSource -> _state.update {
                val next = it.expandedSources.toMutableSet()
                if (event.sourceName in next) next.remove(event.sourceName) else next.add(event.sourceName)
                it.copy(expandedSources = next)
            }
            is StorageAnalyticsEvent.DeleteMangaDownloads -> {
                viewModelScope.launch {
                    _state.update { it.copy(isLoading = true) }
                    val deleted = withContext(Dispatchers.IO) {
                        val root = context.getExternalFilesDir(null) ?: context.filesDir
                        File(root, "OtakuReader/${event.sourceName}/${event.mangaTitle}").deleteRecursively()
                    }
                    _effects.send(
                        if (deleted) StorageAnalyticsEffect.DeleteSuccess(event.mangaTitle)
                        else StorageAnalyticsEffect.DeleteFailure(event.mangaTitle)
                    )
                    load()
                }
            }
            StorageAnalyticsEvent.Refresh -> load()
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { scanDownloadDir() }
            _state.update {
                it.copy(
                    isLoading = false,
                    sources = sources,
                    totalBytes = sources.sumOf { s -> s.totalBytes },
                )
            }
        }
    }

    private fun scanDownloadDir(): List<SourceStorageEntry> {
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "OtakuReader")
        if (!root.exists()) return emptyList()

        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.map { sourceDir ->
                val mangaEntries = sourceDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { mangaDir ->
                        MangaStorageEntry(
                            sourceName = sourceDir.name,
                            title = mangaDir.name,
                            totalBytes = mangaDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                        )
                    }
                    ?.sortedByDescending { it.totalBytes }
                    ?: emptyList()
                SourceStorageEntry(
                    sourceName = sourceDir.name,
                    totalBytes = mangaEntries.sumOf { it.totalBytes },
                    manga = mangaEntries,
                )
            }
            ?.sortedByDescending { it.totalBytes }
            ?: emptyList()
    }
}
