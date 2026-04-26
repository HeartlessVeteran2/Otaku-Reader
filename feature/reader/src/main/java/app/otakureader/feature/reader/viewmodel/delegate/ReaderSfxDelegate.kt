package app.otakureader.feature.reader.viewmodel.delegate

import app.otakureader.core.preferences.AiPreferences
import app.otakureader.domain.usecase.ai.TranslateSfxUseCase
import app.otakureader.feature.reader.viewmodel.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class ReaderSfxDelegate @Inject constructor(
    private val aiPreferences: AiPreferences,
    private val translateSfx: TranslateSfxUseCase,
) {
    private val sfxPageJobs = mutableMapOf<Int, Job>()

    fun observeSettings(
        scope: CoroutineScope,
        updateState: ((ReaderState) -> ReaderState) -> Unit,
    ) {
        combine(aiPreferences.aiEnabled, aiPreferences.aiSfxTranslation) { a, b -> a && b }
            .onEach { enabled -> updateState { it.copy(sfxTranslationEnabled = enabled) } }
            .launchIn(scope)
    }

    fun loadTranslationsForPage(
        scope: CoroutineScope,
        pageIndex: Int,
        pageUrl: String?,
        chapterId: Long,
        updateState: ((ReaderState) -> ReaderState) -> Unit,
    ) {
        if (pageUrl.isNullOrBlank()) return
        if (sfxPageJobs[pageIndex]?.isActive == true) return

        sfxPageJobs[pageIndex] = scope.launch {
            updateState { it.copy(isSfxTranslating = true) }
            val result = translateSfx(
                chapterId = chapterId,
                pageIndex = pageIndex,
                pageImageUrl = pageUrl,
            )
            val translations = result.getOrNull() ?: emptyList()
            updateState { state ->
                state.copy(
                    sfxTranslations = state.sfxTranslations + (pageIndex to translations),
                    isSfxTranslating = false,
                )
            }
        }.also { job -> job.invokeOnCompletion { sfxPageJobs.remove(pageIndex) } }
    }

    fun translateManualText(
        scope: CoroutineScope,
        sfxText: String,
        updateState: ((ReaderState) -> ReaderState) -> Unit,
    ) {
        if (sfxText.isBlank()) return
        scope.launch {
            updateState { it.copy(isSfxTranslating = true) }
            try {
                val result = translateSfx(sfxText = sfxText)
                updateState { state ->
                    val manualTranslations = state.sfxTranslations[TranslateSfxUseCase.MANUAL_PAGE_INDEX]
                        ?.toMutableList() ?: mutableListOf()
                    result.getOrNull()?.let { translation ->
                        val idx = manualTranslations.indexOfFirst { it.originalText == translation.originalText }
                        if (idx >= 0) manualTranslations[idx] = translation else manualTranslations.add(translation)
                    }
                    state.copy(
                        sfxTranslations = state.sfxTranslations +
                            (TranslateSfxUseCase.MANUAL_PAGE_INDEX to manualTranslations.toList()),
                    )
                }
            } finally {
                updateState { it.copy(isSfxTranslating = false) }
            }
        }
    }

    fun clear() {
        sfxPageJobs.values.forEach { it.cancel() }
        sfxPageJobs.clear()
    }
}
