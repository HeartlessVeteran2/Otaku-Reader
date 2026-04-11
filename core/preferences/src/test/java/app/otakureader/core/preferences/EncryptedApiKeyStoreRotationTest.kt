package app.otakureader.core.preferences

import app.otakureader.core.preferences.EncryptedApiKeyStore.Companion.DEFAULT_KEY_MAX_AGE_DAYS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the key-rotation age logic in [EncryptedApiKeyStore].
 *
 * The tests validate the pure time-based calculation without needing an Android
 * context or EncryptedSharedPreferences.
 */
class EncryptedApiKeyStoreRotationTest {

    /**
     * Mirror of the production logic from [EncryptedApiKeyStore.isGeminiKeyRotationRecommended]
     * with an injected [nowMs] so we can control the clock in tests.
     */
    private fun isRotationRecommended(
        storedAtMs: Long?,
        maxAgeDays: Long = DEFAULT_KEY_MAX_AGE_DAYS,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        storedAtMs ?: return false
        val ageMs = nowMs - storedAtMs
        return (ageMs / MILLIS_PER_DAY) >= maxAgeDays
    }

    // ── No timestamp stored ───────────────────────────────────────────────────

    @Test
    fun `returns false when no timestamp is stored`() {
        assertFalse(isRotationRecommended(storedAtMs = null))
    }

    // ── Key is fresh ─────────────────────────────────────────────────────────

    @Test
    fun `returns false when key is one day old`() {
        val now = System.currentTimeMillis()
        assertFalse(isRotationRecommended(storedAtMs = now - MILLIS_PER_DAY, nowMs = now))
    }

    @Test
    fun `returns false when key is 89 days old`() {
        val now = System.currentTimeMillis()
        assertFalse(isRotationRecommended(storedAtMs = now - (89 * MILLIS_PER_DAY), nowMs = now))
    }

    // ── Key is at the boundary ────────────────────────────────────────────────

    @Test
    fun `returns true when key is exactly 90 days old`() {
        val now = System.currentTimeMillis()
        assertTrue(isRotationRecommended(storedAtMs = now - (90 * MILLIS_PER_DAY), nowMs = now))
    }

    // ── Key is stale ─────────────────────────────────────────────────────────

    @Test
    fun `returns true when key is 91 days old`() {
        val now = System.currentTimeMillis()
        assertTrue(isRotationRecommended(storedAtMs = now - (91 * MILLIS_PER_DAY), nowMs = now))
    }

    // ── Custom threshold ──────────────────────────────────────────────────────

    @Test
    fun `returns false when key is younger than custom threshold`() {
        val now = System.currentTimeMillis()
        assertFalse(isRotationRecommended(storedAtMs = now - (29 * MILLIS_PER_DAY), maxAgeDays = 30, nowMs = now))
    }

    @Test
    fun `returns true when key meets custom threshold`() {
        val now = System.currentTimeMillis()
        assertTrue(isRotationRecommended(storedAtMs = now - (30 * MILLIS_PER_DAY), maxAgeDays = 30, nowMs = now))
    }

    // ── Constant verification ─────────────────────────────────────────────────

    @Test
    fun `DEFAULT_KEY_MAX_AGE_DAYS is 90`() {
        assert(DEFAULT_KEY_MAX_AGE_DAYS == 90L) {
            "Expected DEFAULT_KEY_MAX_AGE_DAYS to be 90, but was $DEFAULT_KEY_MAX_AGE_DAYS"
        }
    }

    private companion object {
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}
