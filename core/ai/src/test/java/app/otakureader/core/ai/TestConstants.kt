package app.otakureader.core.ai

/**
 * Test constants for AI tests.
 *
 * These are clearly fake API keys used for testing purposes only.
 * They use a TEST_GEMINI_API_KEY_* format (not the real AIza prefix)
 * to avoid false positives from security scanners.
 */
object TestConstants {
    /**
     * Fake API key for testing. NOT a real Gemini API key.
     * Uses TEST_GEMINI_API_KEY_* format to prevent secret scanner false positives.
     */
    const val FAKE_API_KEY_1 = "TEST_GEMINI_API_KEY_ValidKey12345678901234567890"
    const val FAKE_API_KEY_2 = "TEST_GEMINI_API_KEY_Key123"
    const val FAKE_API_KEY_3 = "TEST_GEMINI_API_KEY_Key456"
    const val FAKE_API_KEY_4 = "TEST_GEMINI_API_KEY_OldKey123"
    const val FAKE_API_KEY_5 = "TEST_GEMINI_API_KEY_NewKey456"
    const val FAKE_API_KEY_6 = "TEST_GEMINI_API_KEY_DifferentKey456"
    const val FAKE_API_KEY_7 = "TEST_GEMINI_API_KEY_SomeFakeKey"
}
