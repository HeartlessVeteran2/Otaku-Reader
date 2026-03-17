package app.otakureader.domain.ai

/**
 * Enumeration of individual AI-powered features in the application.
 *
 * Each entry maps to a per-feature toggle in [AiPreferences] and is used by
 * [AiFeatureGate] to determine whether a specific feature is currently enabled.
 *
 * **Serialization stability**: The [serializedName] property provides stable keys
 * for persistence (preferences, database, analytics). Enum entries can be renamed
 * or reordered without breaking stored values, as long as [serializedName] remains
 * constant. When adding new features, assign a new unique [serializedName].
 * When removing features, keep the enum entry but mark it as deprecated.
 */
enum class AiFeature(
    /**
     * Stable serialization key for this feature. Used for persistence in preferences,
     * database, and analytics. Must remain constant across releases to maintain
     * compatibility with stored settings.
     */
    val serializedName: String
) {
    /** AI-powered reading statistics and insights. */
    READING_INSIGHTS("reading_insights"),

    /** Natural-language smart search queries. */
    SMART_SEARCH("smart_search"),

    /** Personalised manga recommendations based on reading history. */
    RECOMMENDATIONS("recommendations"),

    /** Panel-aware reader using Gemini Vision. */
    PANEL_READER("panel_reader"),

    /** In-page sound-effect translation. */
    SFX_TRANSLATION("sfx_translation"),

    /** Automatic translation of chapter summaries. */
    SUMMARY_TRANSLATION("summary_translation"),

    /** Intelligent scoring and ranking of manga sources. */
    SOURCE_INTELLIGENCE("source_intelligence"),

    /** Context-aware update notification summaries. */
    SMART_NOTIFICATIONS("smart_notifications"),

    /** Automatic manga categorisation when added to the library. */
    AUTO_CATEGORIZATION("auto_categorization");

    companion object {
        /**
         * Pre-computed map of serializedName -> AiFeature for O(1) lookup.
         * Initialized lazily on first access (thread-safe).
         */
        private val BY_SERIALIZED_NAME: Map<String, AiFeature> by lazy {
            val map = entries.associateBy { it.serializedName }

            // Runtime validation: Ensure all serializedNames are unique.
            // This will throw if there are any duplicates, failing fast during initialization.
            require(map.size == entries.size) {
                val duplicates = entries.groupBy { it.serializedName }
                    .filter { it.value.size > 1 }
                    .keys
                "Duplicate serializedName values found: $duplicates. " +
                    "Each AiFeature must have a unique serializedName for stable persistence."
            }

            map
        }

        /**
         * Lookup an [AiFeature] by its [serializedName].
         *
         * This method performs an O(1) lookup using a precomputed map,
         * making it efficient even with large enums or frequent lookups.
         *
         * @return The matching [AiFeature], or `null` if [serializedName] is not recognized.
         */
        fun fromSerializedName(serializedName: String): AiFeature? =
            BY_SERIALIZED_NAME[serializedName]
    }
}
