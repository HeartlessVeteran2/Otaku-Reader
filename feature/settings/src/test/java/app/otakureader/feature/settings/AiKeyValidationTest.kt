package app.otakureader.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [isGeminiApiKeyFormatValid].
 *
 * Covers the validation logic strengthened in M-12/M-13:
 * - Must start with "AIza" prefix
 * - Must be at least 20 characters long
 * - Rejects blank, short, or wrongly-prefixed keys
 */
class AiKeyValidationTest {

    // ── Valid keys ────────────────────────────────────────────────────────────

    @Test
    fun `valid key with AIza prefix and sufficient length returns true`() {
        // A typical Gemini key is 39 chars starting with AIza
        assertTrue(isGeminiApiKeyFormatValid("AIzaSyD-9tSrke72I6e49xbc7ABCDEFGHIJKLMN"))
    }

    @Test
    fun `key exactly 39 chars starting with AIza and valid charset returns true`() {
        // Exactly 39 characters matching [A-Za-z0-9_-]
        assertTrue(isGeminiApiKeyFormatValid("AIzaSyD-9tSrke72I6e49xbc7ABCDEFGHIJKLMN"))
    }

    @Test
    fun `key longer than 39 chars starting with AIza returns false`() {
        assertFalse(isGeminiApiKeyFormatValid("AIzaSyD-9tSrke72I6e49xbc7ABCDEFGHIJKLMNO_extra"))
    }

    // ── Invalid keys ──────────────────────────────────────────────────────────

    @Test
    fun `blank key returns false`() {
        assertFalse(isGeminiApiKeyFormatValid(""))
    }

    @Test
    fun `whitespace-only key returns false`() {
        assertFalse(isGeminiApiKeyFormatValid("   "))
    }

    @Test
    fun `key without AIza prefix returns false`() {
        assertFalse(isGeminiApiKeyFormatValid("sk-abcdefghijklmnopqrstuvwxyz1234567890"))
    }

    @Test
    fun `key with wrong prefix returns false`() {
        assertFalse(isGeminiApiKeyFormatValid("BIzaSyD-9tSrke72I6e49xbc7ABCDEFGHIJK"))
    }

    @Test
    fun `key shorter than 20 chars returns false even with correct prefix`() {
        // "AIza" + 15 chars = 19 chars total — one short of minimum
        assertFalse(isGeminiApiKeyFormatValid("AIzaXXXXXXXXXXXXXXX"))
    }

    @Test
    fun `key that is exactly AIza prefix only returns false`() {
        assertFalse(isGeminiApiKeyFormatValid("AIza"))
    }

    @Test
    fun `key with lowercase aiza prefix returns false`() {
        // Prefix check is case-sensitive
        assertFalse(isGeminiApiKeyFormatValid("aizaSyD-9tSrke72I6e49xbc7ABCDEFGHIJK"))
    }
}
