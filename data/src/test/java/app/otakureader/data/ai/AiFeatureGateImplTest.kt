package app.otakureader.data.ai

import app.otakureader.core.preferences.AiPreferences
import app.otakureader.data.TestConstants.FAKE_API_KEY
import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.repository.AiRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiFeatureGateImplTest {

    private lateinit var aiPreferences: AiPreferences
    private lateinit var aiRepository: AiRepository
    private lateinit var gate: AiFeatureGateImpl

    @Before
    fun setUp() {
        // relaxed = true so that the featureFlows map built during AiFeatureGateImpl
        // construction (which accesses all aiPreferences.ai* Flow properties) does not
        // throw MockKException for unstubbed calls in tests that don't exercise those flows.
        aiPreferences = mockk(relaxed = true)
        aiRepository = mockk()
        gate = AiFeatureGateImpl(aiPreferences, aiRepository)
    }

    // ---- isAiAvailable ----

    @Test
    fun `isAiAvailable returns false when master toggle is off`() = runTest {
        every { aiPreferences.aiEnabled } returns flowOf(false)

        assertFalse(gate.isAiAvailable())
    }

    @Test
    fun `isAiAvailable returns false when toggle is on but API key is blank`() = runTest {
        every { aiPreferences.aiEnabled } returns flowOf(true)
        coEvery { aiPreferences.getGeminiApiKey() } returns ""

        assertFalse(gate.isAiAvailable())
    }

    @Test
    fun `isAiAvailable returns false when key set but backend not initialized`() = runTest {
        every { aiPreferences.aiEnabled } returns flowOf(true)
        coEvery { aiPreferences.getGeminiApiKey() } returns FAKE_API_KEY
        coEvery { aiRepository.isAvailable() } returns false

        assertFalse(gate.isAiAvailable())
    }

    @Test
    fun `isAiAvailable returns true when toggle on, key set, and backend available`() = runTest {
        every { aiPreferences.aiEnabled } returns flowOf(true)
        coEvery { aiPreferences.getGeminiApiKey() } returns FAKE_API_KEY
        coEvery { aiRepository.isAvailable() } returns true

        assertTrue(gate.isAiAvailable())
    }

    // ---- isFeatureAvailable ----

    @Test
    fun `isFeatureAvailable returns false when global AI is unavailable`() = runTest {
        every { aiPreferences.aiEnabled } returns flowOf(false)

        assertFalse(gate.isFeatureAvailable(AiFeature.READING_INSIGHTS))
    }

    @Test
    fun `isFeatureAvailable returns false when feature toggle is off`() = runTest {
        every { aiPreferences.aiEnabled } returns flowOf(true)
        coEvery { aiPreferences.getGeminiApiKey() } returns FAKE_API_KEY
        coEvery { aiRepository.isAvailable() } returns true
        every { aiPreferences.aiReadingInsights } returns flowOf(false)

        assertFalse(gate.isFeatureAvailable(AiFeature.READING_INSIGHTS))
    }

    @Test
    fun `isFeatureAvailable returns true when global AI available and feature toggle on`() = runTest {
        every { aiPreferences.aiEnabled } returns flowOf(true)
        coEvery { aiPreferences.getGeminiApiKey() } returns FAKE_API_KEY
        coEvery { aiRepository.isAvailable() } returns true
        every { aiPreferences.aiSmartSearch } returns flowOf(true)

        assertTrue(gate.isFeatureAvailable(AiFeature.SMART_SEARCH))
    }

    @Test
    fun `isFeatureAvailable checks the correct toggle for each feature`() = runTest {
        every { aiPreferences.aiEnabled } returns flowOf(true)
        coEvery { aiPreferences.getGeminiApiKey() } returns FAKE_API_KEY
        coEvery { aiRepository.isAvailable() } returns true
        every { aiPreferences.aiReadingInsights } returns flowOf(true)
        every { aiPreferences.aiSmartSearch } returns flowOf(false)
        every { aiPreferences.aiRecommendations } returns flowOf(true)
        every { aiPreferences.aiPanelReader } returns flowOf(false)
        every { aiPreferences.aiSfxTranslation } returns flowOf(true)
        every { aiPreferences.aiSummaryTranslation } returns flowOf(false)
        every { aiPreferences.aiSourceIntelligence } returns flowOf(true)
        every { aiPreferences.aiSmartNotifications } returns flowOf(false)
        every { aiPreferences.aiAutoCategorization } returns flowOf(true)

        assertTrue(gate.isFeatureAvailable(AiFeature.READING_INSIGHTS))
        assertFalse(gate.isFeatureAvailable(AiFeature.SMART_SEARCH))
        assertTrue(gate.isFeatureAvailable(AiFeature.RECOMMENDATIONS))
        assertFalse(gate.isFeatureAvailable(AiFeature.PANEL_READER))
        assertTrue(gate.isFeatureAvailable(AiFeature.SFX_TRANSLATION))
        assertFalse(gate.isFeatureAvailable(AiFeature.SUMMARY_TRANSLATION))
        assertTrue(gate.isFeatureAvailable(AiFeature.SOURCE_INTELLIGENCE))
        assertFalse(gate.isFeatureAvailable(AiFeature.SMART_NOTIFICATIONS))
        assertTrue(gate.isFeatureAvailable(AiFeature.AUTO_CATEGORIZATION))
    }
}
