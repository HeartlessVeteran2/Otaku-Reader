package app.otakureader.feature.settings

/**
 * Returns `true` if [key] is a plausibly valid Gemini API key format.
 *
 * Gemini keys follow the Google API key convention: they start with the prefix
 * `AIza` and are at least 20 characters long. This is a lightweight format
 * check — it does **not** verify the key against the Gemini service.
 *
 * Used from both [SettingsViewModel] (validation before persist) and
 * [SettingsScreen] (disabling the Save button on obviously wrong input).
 */
// M-13: Prefix extracted to a named constant rather than a bare string literal.
private const val GOOGLE_API_KEY_PREFIX = "AIza"

// M-12: Exact key length for Google API keys.
private const val GOOGLE_API_KEY_LENGTH = 39

// M-12: Restrict character set to [A-Za-z0-9_-] — the only characters
// that appear in real Google API keys.
private val GOOGLE_API_KEY_REGEX = Regex("^[A-Za-z0-9_-]{$GOOGLE_API_KEY_LENGTH}$")

internal fun isGeminiApiKeyFormatValid(key: String): Boolean =
    key.startsWith(GOOGLE_API_KEY_PREFIX) &&
        key.length == GOOGLE_API_KEY_LENGTH &&
        GOOGLE_API_KEY_REGEX.matches(key)
