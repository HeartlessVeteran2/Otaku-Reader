package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for AI-related settings.
 * All AI features are opt-in and can be completely disabled.
 */
class AiPreferences(private val dataStore: DataStore<Preferences>) {

    /** Master switch for all AI features. When false, no AI features work regardless of other settings. */
    val aiEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.AI_ENABLED] ?: false }
    suspend fun setAiEnabled(value: Boolean) = dataStore.edit { it[Keys.AI_ENABLED] = value }

    /**
     * AI tier selection:
     * 0 = Free tier (limited requests)
     * 1 = Standard tier (paid, higher limits)
     * 2 = Pro tier (paid, highest limits)
     */
    val aiTier: Flow<Int> = dataStore.data.map { it[Keys.AI_TIER] ?: 0 }
    suspend fun setAiTier(value: Int) = dataStore.edit { it[Keys.AI_TIER] = value }

    /** Gemini API key (masked in UI, stored locally). */
    val geminiApiKey: Flow<String> = dataStore.data.map { it[Keys.GEMINI_API_KEY] ?: "" }
    suspend fun setGeminiApiKey(value: String) = dataStore.edit { it[Keys.GEMINI_API_KEY] = value }

    // --- Individual Feature Toggles ---

    /** Enable AI-powered reading insights and statistics. */
    val aiReadingInsights: Flow<Boolean> = dataStore.data.map { it[Keys.AI_READING_INSIGHTS] ?: false }
    suspend fun setAiReadingInsights(value: Boolean) = dataStore.edit { it[Keys.AI_READING_INSIGHTS] = value }

    /** Enable smart search with natural language queries. */
    val aiSmartSearch: Flow<Boolean> = dataStore.data.map { it[Keys.AI_SMART_SEARCH] ?: true }
    suspend fun setAiSmartSearch(value: Boolean) = dataStore.edit { it[Keys.AI_SMART_SEARCH] = value }

    /** Enable AI recommendations based on reading history. */
    val aiRecommendations: Flow<Boolean> = dataStore.data.map { it[Keys.AI_RECOMMENDATIONS] ?: true }
    suspend fun setAiRecommendations(value: Boolean) = dataStore.edit { it[Keys.AI_RECOMMENDATIONS] = value }

    /** Enable panel-aware reader with Gemini Vision. */
    val aiPanelReader: Flow<Boolean> = dataStore.data.map { it[Keys.AI_PANEL_READER] ?: true }
    suspend fun setAiPanelReader(value: Boolean) = dataStore.edit { it[Keys.AI_PANEL_READER] = value }

    /** Enable SFX translation in manga pages. */
    val aiSfxTranslation: Flow<Boolean> = dataStore.data.map { it[Keys.AI_SFX_TRANSLATION] ?: true }
    suspend fun setAiSfxTranslation(value: Boolean) = dataStore.edit { it[Keys.AI_SFX_TRANSLATION] = value }

    /** Enable auto-translation of chapter summaries. */
    val aiSummaryTranslation: Flow<Boolean> = dataStore.data.map { it[Keys.AI_SUMMARY_TRANSLATION] ?: true }
    suspend fun setAiSummaryTranslation(value: Boolean) = dataStore.edit { it[Keys.AI_SUMMARY_TRANSLATION] = value }

    /** Enable source intelligence for best source scoring. */
    val aiSourceIntelligence: Flow<Boolean> = dataStore.data.map { it[Keys.AI_SOURCE_INTELLIGENCE] ?: true }
    suspend fun setAiSourceIntelligence(value: Boolean) = dataStore.edit { it[Keys.AI_SOURCE_INTELLIGENCE] = value }

    /** Enable smart notifications with context-aware summaries. */
    val aiSmartNotifications: Flow<Boolean> = dataStore.data.map { it[Keys.AI_SMART_NOTIFICATIONS] ?: true }
    suspend fun setAiSmartNotifications(value: Boolean) = dataStore.edit { it[Keys.AI_SMART_NOTIFICATIONS] = value }

    /** Enable auto-categorization of manga when added to library. */
    val aiAutoCategorization: Flow<Boolean> = dataStore.data.map { it[Keys.AI_AUTO_CATEGORIZATION] ?: true }
    suspend fun setAiAutoCategorization(value: Boolean) = dataStore.edit { it[Keys.AI_AUTO_CATEGORIZATION] = value }

    // --- Usage Tracking ---

    /** Total tokens used this month (for quota tracking). */
    val aiTokensUsedThisMonth: Flow<Long> = dataStore.data.map { it[Keys.AI_TOKENS_USED_THIS_MONTH] ?: 0L }
    suspend fun setAiTokensUsedThisMonth(value: Long) = dataStore.edit { it[Keys.AI_TOKENS_USED_THIS_MONTH] = value }

    /** Month-year for token tracking (format: YYYY-MM). */
    val aiTokenTrackingPeriod: Flow<String> = dataStore.data.map { it[Keys.AI_TOKEN_TRACKING_PERIOD] ?: "" }
    suspend fun setAiTokenTrackingPeriod(value: String) = dataStore.edit { it[Keys.AI_TOKEN_TRACKING_PERIOD] = value }

    /** Last time AI cache was cleared. */
    val aiCacheLastCleared: Flow<Long> = dataStore.data.map { it[Keys.AI_CACHE_LAST_CLEARED] ?: 0L }
    suspend fun setAiCacheLastCleared(value: Long) = dataStore.edit { it[Keys.AI_CACHE_LAST_CLEARED] = value }

    private object Keys {
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_TIER = intPreferencesKey("ai_tier")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")

        val AI_READING_INSIGHTS = booleanPreferencesKey("ai_reading_insights")
        val AI_SMART_SEARCH = booleanPreferencesKey("ai_smart_search")
        val AI_RECOMMENDATIONS = booleanPreferencesKey("ai_recommendations")
        val AI_PANEL_READER = booleanPreferencesKey("ai_panel_reader")
        val AI_SFX_TRANSLATION = booleanPreferencesKey("ai_sfx_translation")
        val AI_SUMMARY_TRANSLATION = booleanPreferencesKey("ai_summary_translation")
        val AI_SOURCE_INTELLIGENCE = booleanPreferencesKey("ai_source_intelligence")
        val AI_SMART_NOTIFICATIONS = booleanPreferencesKey("ai_smart_notifications")
        val AI_AUTO_CATEGORIZATION = booleanPreferencesKey("ai_auto_categorization")

        val AI_TOKENS_USED_THIS_MONTH = longPreferencesKey("ai_tokens_used_this_month")
        val AI_TOKEN_TRACKING_PERIOD = stringPreferencesKey("ai_token_tracking_period")
        val AI_CACHE_LAST_CLEARED = longPreferencesKey("ai_cache_last_cleared")
    }
}