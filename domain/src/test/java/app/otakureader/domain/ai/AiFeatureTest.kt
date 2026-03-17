package app.otakureader.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiFeatureTest {

    // ---- Serialized names are stable ----

    @Test
    fun `serializedName values are stable and match expected format`() {
        // These values must never change, as they may be persisted in preferences/database
        assertEquals("reading_insights", AiFeature.READING_INSIGHTS.serializedName)
        assertEquals("smart_search", AiFeature.SMART_SEARCH.serializedName)
        assertEquals("recommendations", AiFeature.RECOMMENDATIONS.serializedName)
        assertEquals("panel_reader", AiFeature.PANEL_READER.serializedName)
        assertEquals("sfx_translation", AiFeature.SFX_TRANSLATION.serializedName)
        assertEquals("summary_translation", AiFeature.SUMMARY_TRANSLATION.serializedName)
        assertEquals("source_intelligence", AiFeature.SOURCE_INTELLIGENCE.serializedName)
        assertEquals("smart_notifications", AiFeature.SMART_NOTIFICATIONS.serializedName)
        assertEquals("auto_categorization", AiFeature.AUTO_CATEGORIZATION.serializedName)
    }

    @Test
    fun `all serializedNames are unique`() {
        val names = AiFeature.entries.map { it.serializedName }
        val uniqueNames = names.toSet()

        assertEquals("All serializedNames must be unique", names.size, uniqueNames.size)
    }

    @Test
    fun `all serializedNames use snake_case format`() {
        AiFeature.entries.forEach { feature ->
            val name = feature.serializedName
            // Should be lowercase with underscores only (no uppercase, no hyphens, no spaces)
            assertEquals(
                "serializedName should be snake_case: ${feature.name}",
                name,
                name.lowercase().replace('-', '_').replace(' ', '_')
            )
        }
    }

    // ---- fromSerializedName lookup ----

    @Test
    fun `fromSerializedName returns correct feature for valid names`() {
        assertEquals(AiFeature.READING_INSIGHTS, AiFeature.fromSerializedName("reading_insights"))
        assertEquals(AiFeature.SMART_SEARCH, AiFeature.fromSerializedName("smart_search"))
        assertEquals(AiFeature.RECOMMENDATIONS, AiFeature.fromSerializedName("recommendations"))
        assertEquals(AiFeature.PANEL_READER, AiFeature.fromSerializedName("panel_reader"))
        assertEquals(AiFeature.SFX_TRANSLATION, AiFeature.fromSerializedName("sfx_translation"))
        assertEquals(AiFeature.SUMMARY_TRANSLATION, AiFeature.fromSerializedName("summary_translation"))
        assertEquals(AiFeature.SOURCE_INTELLIGENCE, AiFeature.fromSerializedName("source_intelligence"))
        assertEquals(AiFeature.SMART_NOTIFICATIONS, AiFeature.fromSerializedName("smart_notifications"))
        assertEquals(AiFeature.AUTO_CATEGORIZATION, AiFeature.fromSerializedName("auto_categorization"))
    }

    @Test
    fun `fromSerializedName returns null for unknown name`() {
        assertNull(AiFeature.fromSerializedName("unknown_feature"))
        assertNull(AiFeature.fromSerializedName(""))
        assertNull(AiFeature.fromSerializedName("READING_INSIGHTS")) // Wrong case
    }

    @Test
    fun `fromSerializedName round-trip preserves feature`() {
        AiFeature.entries.forEach { feature ->
            val serialized = feature.serializedName
            val deserialized = AiFeature.fromSerializedName(serialized)
            assertNotNull("Round-trip failed for ${feature.name}", deserialized)
            assertEquals("Round-trip failed for ${feature.name}", feature, deserialized)
        }
    }

    // ---- Enum stability ----

    @Test
    fun `enum count is expected value`() {
        // If this test fails after adding/removing features, update the count
        // and verify that serializedNames are still stable
        assertEquals(9, AiFeature.entries.size)
    }

    @Test
    fun `enum entries order should not matter for serialization`() {
        // The serializedName property allows enum entries to be reordered without
        // breaking persistence. This test just documents that fact.
        val firstEntry = AiFeature.entries[0]
        assertNotNull(firstEntry.serializedName)
    }
}
