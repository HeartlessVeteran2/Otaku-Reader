package app.otakureader.core.ai.datastore

import app.otakureader.core.ai.datastore.SecureApiKeyDataStore.Companion.DEFAULT_KEY_MAX_AGE_DAYS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the key-rotation age logic extracted from [SecureApiKeyDataStore].
 *
 * These tests validate the pure time-based calculation without needing an Android
 * context, DataStore, or EncryptedSharedPreferences.
 */
class SecureApiKeyDataStoreRotationTest {

    // ── isRotationRecommended helper ──────────────────────────────────────────

    /**
     * Mirror of the production logic so we can test it in isolation using
     * an injected [nowMs] instead of [System.currentTimeMillis].
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
    fun `returns false when key is just one day old`() {
        val now = System.currentTimeMillis()
        val storedAt = now - MILLIS_PER_DAY // 1 day ago
        assertFalse(isRotationRecommended(storedAtMs = storedAt, nowMs = now))
    }

    @Test
    fun `returns false when key is 89 days old with default threshold`() {
        val now = System.currentTimeMillis()
        val storedAt = now - (89 * MILLIS_PER_DAY)
        assertFalse(isRotationRecommended(storedAtMs = storedAt, nowMs = now))
    }

    // ── Key is at the boundary ────────────────────────────────────────────────

    @Test
    fun `returns true when key is exactly 90 days old`() {
        val now = System.currentTimeMillis()
        val storedAt = now - (90 * MILLIS_PER_DAY)
        assertTrue(isRotationRecommended(storedAtMs = storedAt, nowMs = now))
    }

    // ── Key is stale ─────────────────────────────────────────────────────────

    @Test
    fun `returns true when key is 91 days old`() {
        val now = System.currentTimeMillis()
        val storedAt = now - (91 * MILLIS_PER_DAY)
        assertTrue(isRotationRecommended(storedAtMs = storedAt, nowMs = now))
    }

    @Test
    fun `returns true when key is one year old`() {
        val now = System.currentTimeMillis()
        val storedAt = now - (365 * MILLIS_PER_DAY)
        assertTrue(isRotationRecommended(storedAtMs = storedAt, nowMs = now))
    }

    // ── Custom threshold ──────────────────────────────────────────────────────

    @Test
    fun `returns false when key is 29 days old with 30-day threshold`() {
        val now = System.currentTimeMillis()
        val storedAt = now - (29 * MILLIS_PER_DAY)
        assertFalse(isRotationRecommended(storedAtMs = storedAt, maxAgeDays = 30, nowMs = now))
    }

    @Test
    fun `returns true when key is exactly at custom threshold`() {
        val now = System.currentTimeMillis()
        val storedAt = now - (30 * MILLIS_PER_DAY)
        assertTrue(isRotationRecommended(storedAtMs = storedAt, maxAgeDays = 30, nowMs = now))
    }

    // ── DEFAULT_KEY_MAX_AGE_DAYS constant ─────────────────────────────────────

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
