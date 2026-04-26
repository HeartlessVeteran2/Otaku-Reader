package app.otakureader.data.ai

import app.otakureader.core.preferences.AiPreferences
import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.repository.AiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [AiFeatureGate].
 *
 * Feature availability is determined by checking (in order):
 * 1. The master AI toggle ([AiPreferences.aiEnabled]).
 * 2. The presence of a non-blank Gemini API key ([AiPreferences.getGeminiApiKey]).
 * 3. The AI backend is initialized and ready ([AiRepository.isAvailable]).
 *    In FOSS/noop builds this always returns `false`, ensuring the gate stays closed
 *    even if preferences claim a key is set.
 * 4. The per-feature toggle for the requested [AiFeature].
 *
 * This class reads preference flows on each call so that toggling a setting in the
 * UI takes effect immediately without requiring an app restart.
 *
 * The [featureFlows] map is built once at construction time. Whenever a new [AiFeature]
 * is added the compiler will warn via an exhaustive `when` in [featureFlows] if the
 * entry is missing, preventing silent "always disabled" regressions.
 */
@Singleton
class AiFeatureGateImpl @Inject constructor(
    private val aiPreferences: AiPreferences,
    private val aiRepository: AiRepository,
) : AiFeatureGate {

    /**
     * Mapping from each [AiFeature] to a lambda that retrieves the controlling [Flow<Boolean>].
     *
     * Using lambdas (instead of caching `Flow` instances directly) ensures each invocation
     * reads the current value from [aiPreferences] rather than a snapshot taken at
     * construction time. This makes the gate correctly react to preference changes at
     * runtime and is also more test-friendly (stubs can be changed after construction).
     *
     * The exhaustive `when` inside [AiFeature.entries.associateWith] ensures a compilation
     * error if a new [AiFeature] entry is added without a corresponding preference flow.
     */
    private val featureFlowProviders: Map<AiFeature, () -> Flow<Boolean>> =
        AiFeature.entries.associateWith { feature ->
            when (feature) {
                AiFeature.READING_INSIGHTS -> { -> aiPreferences.aiReadingInsights }
                AiFeature.SMART_SEARCH -> { -> aiPreferences.aiSmartSearch }
                AiFeature.RECOMMENDATIONS -> { -> aiPreferences.aiRecommendations }
                AiFeature.PANEL_READER -> { -> aiPreferences.aiPanelReader }
                AiFeature.SFX_TRANSLATION -> { -> aiPreferences.aiSfxTranslation }
                AiFeature.SUMMARY_TRANSLATION -> { -> aiPreferences.aiSummaryTranslation }
                AiFeature.SOURCE_INTELLIGENCE -> { -> aiPreferences.aiSourceIntelligence }
                AiFeature.SMART_NOTIFICATIONS -> { -> aiPreferences.aiSmartNotifications }
                AiFeature.AUTO_CATEGORIZATION -> { -> aiPreferences.aiAutoCategorization }
                AiFeature.OCR_TRANSLATION -> { -> aiPreferences.aiOcrTranslation }
            }
        }

    /**
     * Returns `true` when:
     * - The master AI toggle is on,
     * - An API key is configured in preferences, **and**
     * - The AI backend client reports it is initialized and ready.
     *
     * The third check ensures that in FOSS/noop builds (where [AiRepository.isAvailable]
     * always returns `false`) the gate stays closed even if preferences indicate otherwise.
     */
    override suspend fun isAiAvailable(): Boolean {
        val masterEnabled = aiPreferences.aiEnabled.first()
        if (!masterEnabled) return false

        val apiKey = aiPreferences.getGeminiApiKey()
        if (apiKey.isBlank()) return false

        return aiRepository.isAvailable()
    }

    /**
     * Returns `true` when [isAiAvailable] is satisfied and the per-feature toggle for
     * [feature] is also on.
     */
    override suspend fun isFeatureAvailable(feature: AiFeature): Boolean {
        if (!isAiAvailable()) return false

        return featureFlowProviders.getValue(feature)().first()
    }
}
