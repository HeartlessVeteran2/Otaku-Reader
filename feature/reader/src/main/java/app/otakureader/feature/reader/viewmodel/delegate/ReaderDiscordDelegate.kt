package app.otakureader.feature.reader.viewmodel.delegate

import app.otakureader.core.discord.DiscordRpcService
import app.otakureader.core.discord.ReadingStatus
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.feature.reader.viewmodel.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class ReaderDiscordDelegate @Inject constructor(
    private val generalPreferences: GeneralPreferences,
    private val discordRpcService: DiscordRpcService,
) {
    var cachedEnabled: Boolean = false
        private set

    fun startObserving(
        scope: CoroutineScope,
        getCurrentManga: () -> Manga?,
        getCurrentChapter: () -> Chapter?,
        getState: () -> ReaderState,
    ) {
        scope.launch {
            generalPreferences.discordRpcEnabled.collectLatest { enabled ->
                cachedEnabled = enabled
                if (!enabled) {
                    discordRpcService.clearReadingPresence(showBrowsing = false)
                    return@collectLatest
                }
                val manga = getCurrentManga()
                val chapter = getCurrentChapter()
                val pages = getState().pages
                if (manga != null && chapter != null) {
                    val page = if (pages.isNotEmpty()) getState().currentPage + 1 else null
                    updatePresence(manga.title, chapter.name, pages.size, page)
                }
            }
        }
    }

    fun updatePresence(
        mangaTitle: String,
        chapterName: String,
        totalPages: Int,
        currentPage: Int? = null,
    ) {
        if (!cachedEnabled) return
        if (currentPage == null) {
            discordRpcService.resetSessionTimer()
        }
        discordRpcService.updateReadingPresence(
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            status = ReadingStatus.READING,
            page = currentPage,
            totalPages = totalPages,
        )
    }

    fun clearPresence(showBrowsing: Boolean) {
        discordRpcService.clearReadingPresence(showBrowsing = showBrowsing)
    }
}
