package app.otakureader.feature.settings.delegate

import app.otakureader.core.preferences.AiPreferences
import app.otakureader.domain.repository.AiRepository
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_VALIDATION_COOLDOWN_MS = 10_000L

@Singleton
class AiSettingsDelegate @Inject constructor(
    private val aiPreferences: AiPreferences,
    private val aiRepository: AiRepository,
) {

    private var updateState: ((SettingsState) -> SettingsState) -> Unit = {}
    private var lastKeyValidationTimeMs = 0L

    /** Perform any one-time migration; call from ViewModel.init before [startObserving]. */
    suspend fun initAiPrefs() {
        aiPreferences.migrateLegacyApiKeyIfNeeded()
    }

    fun startObserving(
        scope: CoroutineScope,
        updateState: ((SettingsState) -> SettingsState) -> Unit,
    ) {
        this.updateState = updateState
        scope.launch {
            combine(
                aiPreferences.aiEnabled,
                aiPreferences.aiTier,
                aiPreferences.aiReadingInsights,
                aiPreferences.aiSmartSearch,
                aiPreferences.aiRecommendations,
            ) { enabled, tier, insights, smartSearch, recs ->
                updateState { it.copy(
                    aiEnabled = enabled,
                    aiTier = tier,
                    aiApiKeySet = aiPreferences.getGeminiApiKey().isNotBlank(),
                    aiReadingInsights = insights,
                    aiSmartSearch = smartSearch,
                    aiRecommendations = recs,
                ) }
            }.collect { }
        }
        scope.launch {
            combine(
                aiPreferences.aiPanelReader,
                aiPreferences.aiSfxTranslation,
                aiPreferences.aiSummaryTranslation,
                aiPreferences.aiSourceIntelligence,
                aiPreferences.aiSmartNotifications,
            ) { panelReader, sfx, summary, sourceIntel, smartNotif ->
                updateState { it.copy(
                    aiPanelReader = panelReader,
                    aiSfxTranslation = sfx,
                    aiSummaryTranslation = summary,
                    aiSourceIntelligence = sourceIntel,
                    aiSmartNotifications = smartNotif,
                ) }
            }.collect { }
        }
        scope.launch {
            aiPreferences.aiAutoCategorization.collect { autoCat ->
                updateState { it.copy(aiAutoCategorization = autoCat) }
            }
        }
    }

    suspend fun handleEvent(
        event: SettingsEvent,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ): Boolean = when (event) {
        is SettingsEvent.SetAiEnabled -> { aiPreferences.setAiEnabled(event.enabled); true }
        is SettingsEvent.SetAiTier -> { aiPreferences.setAiTier(event.tier); true }
        is SettingsEvent.SetAiApiKey -> { handleSetAiApiKey(event.key, sendEffect); true }
        SettingsEvent.RemoveAiApiKey -> { updateState { it.copy(showRemoveApiKeyDialog = true) }; true }
        SettingsEvent.ConfirmRemoveAiApiKey -> { handleConfirmRemoveAiApiKey(sendEffect); true }
        SettingsEvent.DismissRemoveApiKeyDialog -> { updateState { it.copy(showRemoveApiKeyDialog = false) }; true }
        is SettingsEvent.SetAiReadingInsights -> { aiPreferences.setAiReadingInsights(event.enabled); true }
        is SettingsEvent.SetAiSmartSearch -> { aiPreferences.setAiSmartSearch(event.enabled); true }
        is SettingsEvent.SetAiRecommendations -> { aiPreferences.setAiRecommendations(event.enabled); true }
        is SettingsEvent.SetAiPanelReader -> { aiPreferences.setAiPanelReader(event.enabled); true }
        is SettingsEvent.SetAiSfxTranslation -> { aiPreferences.setAiSfxTranslation(event.enabled); true }
        is SettingsEvent.SetAiSummaryTranslation -> { aiPreferences.setAiSummaryTranslation(event.enabled); true }
        is SettingsEvent.SetAiSourceIntelligence -> { aiPreferences.setAiSourceIntelligence(event.enabled); true }
        is SettingsEvent.SetAiSmartNotifications -> { aiPreferences.setAiSmartNotifications(event.enabled); true }
        is SettingsEvent.SetAiAutoCategorization -> { aiPreferences.setAiAutoCategorization(event.enabled); true }
        SettingsEvent.ClearAiCache -> { handleClearAiCache(sendEffect); true }
        else -> false
    }

    private suspend fun handleSetAiApiKey(key: String, sendEffect: suspend (SettingsEffect) -> Unit) {
        if (key.isNotBlank() && !isGeminiApiKeyFormatValid(key)) {
            sendEffect(SettingsEffect.ShowSnackbar("Invalid API key format"))
            return
        }

        // Rate-limit live validation to once per 10 seconds.
        val now = android.os.SystemClock.elapsedRealtime()
        val canValidate = (now - lastKeyValidationTimeMs) >= KEY_VALIDATION_COOLDOWN_MS

        aiPreferences.setGeminiApiKey(key)
        val persistedKey = aiPreferences.getGeminiApiKey()
        val isSet = persistedKey.isNotBlank()
        updateState { it.copy(aiApiKeySet = isSet) }

        if (key.isNotBlank() && !isSet) {
            sendEffect(SettingsEffect.ShowSnackbar("Failed to save AI API key"))
            return
        }

        if (!isSet) return

        aiRepository.clearApiKey()
        aiRepository.initialize(persistedKey)

        if (canValidate) {
            lastKeyValidationTimeMs = now
            // Make a cheap test call to verify the key actually works with the Gemini API.
            val testResult = runCatching { aiRepository.generateContent("ping") }
            val keyWorks = testResult.isSuccess && testResult.getOrNull()?.isSuccess == true
            sendEffect(
                SettingsEffect.ShowSnackbar(
                    if (keyWorks) "API key verified and saved"
                    else "API key saved — could not verify connectivity (check key validity)"
                )
            )
        } else {
            sendEffect(SettingsEffect.ShowSnackbar("API key saved"))
        }
    }

    private suspend fun handleConfirmRemoveAiApiKey(sendEffect: suspend (SettingsEffect) -> Unit) {
        updateState { it.copy(showRemoveApiKeyDialog = false) }
        aiPreferences.clearGeminiApiKey()
        aiRepository.clearApiKey()
        updateState { it.copy(aiApiKeySet = false) }
        sendEffect(SettingsEffect.ShowSnackbar("AI API key removed"))
    }

    private suspend fun handleClearAiCache(sendEffect: suspend (SettingsEffect) -> Unit) {
        aiPreferences.setAiCacheLastCleared(System.currentTimeMillis())
        sendEffect(SettingsEffect.ShowSnackbar("AI suggestions will refresh for future requests"))
    }

    private fun isGeminiApiKeyFormatValid(key: String): Boolean {
        return key.matches(Regex("^AIza[0-9A-Za-z_-]{35}$"))
    }
}
