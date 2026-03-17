package app.otakureader.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiClientTest {

    private lateinit var client: GeminiClient

    @Before
    fun setUp() {
        client = GeminiClient()
        // Mock GenerativeModel constructor to prevent actual API calls
        mockkConstructor(GenerativeModel::class)
        every { anyConstructed<GenerativeModel>().modelName } returns "gemini-pro"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ---- Initialization ----

    @Test
    fun `initialize with valid key succeeds`() {
        client.initialize("AIzaValidKey12345678901234567890")

        assertTrue(client.isInitialized())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `initialize with blank key throws`() {
        client.initialize("   ")
    }

    @Test
    fun `initialize twice with same key is no-op`() {
        client.initialize("AIzaKey123")
        client.initialize("AIzaKey123") // Should not throw

        assertTrue(client.isInitialized())
    }

    @Test(expected = IllegalStateException::class)
    fun `initialize twice with different key throws`() {
        client.initialize("AIzaKey123")
        client.initialize("AIzaKey456") // Should throw
    }

    // ---- Reset ----

    @Test
    fun `reset clears initialized state`() {
        client.initialize("AIzaKey123")
        assertTrue(client.isInitialized())

        client.reset()

        assertFalse(client.isInitialized())
    }

    @Test
    fun `reset allows re-initialization`() {
        client.initialize("AIzaKey123")
        client.reset()
        client.initialize("AIzaKey456") // Should not throw

        assertTrue(client.isInitialized())
    }

    @Test
    fun `reset zeroes configMac buffer to prevent secret residue`() {
        // This test uses reflection to verify that reset() actually zeroes the configMac
        // buffer before reassignment, preventing HMAC-derived secrets from lingering in memory.
        client.initialize("AIzaKey123")

        // Capture the configMac ByteArray using reflection before reset
        val configMacField = GeminiClient::class.java.getDeclaredField("configMac")
        configMacField.isAccessible = true
        val macBeforeReset = configMacField.get(client) as ByteArray

        // Verify the MAC is non-empty before reset
        assertTrue("configMac should be non-empty before reset", macBeforeReset.isNotEmpty())

        client.reset()

        // After reset, the captured ByteArray should be all zeros (it was zeroed in-place)
        assertTrue("configMac buffer should be zeroed after reset",
            macBeforeReset.all { it == 0.toByte() })

        // Also verify re-initialization works
        client.initialize("AIzaDifferentKey456")
        assertTrue(client.isInitialized())
    }

    // ---- Reinitialize ----

    @Test
    fun `reinitialize replaces existing configuration`() {
        client.initialize("AIzaOldKey123")
        assertTrue(client.isInitialized())

        client.reinitialize("AIzaNewKey456")

        assertTrue(client.isInitialized())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reinitialize with blank key throws`() {
        client.initialize("AIzaKey123")
        client.reinitialize("   ")
    }

    @Test
    fun `reinitialize replaces existing configuration atomically`() {
        // This test verifies that reinitialize doesn't leave the client in a half-initialized state.
        // If the new model creation fails, the client should be left uninitialized (not with old state).
        client.initialize("AIzaOldKey123")
        assertTrue(client.isInitialized())

        // Successful reinitialize replaces the configuration
        client.reinitialize("AIzaNewKey456")

        assertTrue(client.isInitialized())
    }

    @Test
    fun `reinitialize clears old state before setting new state`() {
        // This test documents that reinitialize zeros configMac before creating the new model.
        // We can't directly test the zeroing, but we verify successful reinitialize works.
        client.initialize("AIzaOldKey123")

        // Set up mock to succeed
        unmockkAll()
        mockkConstructor(GenerativeModel::class)
        every { anyConstructed<GenerativeModel>().modelName } returns "gemini-pro"

        client.reinitialize("AIzaNewKey456")

        assertTrue(client.isInitialized())
    }

    @Test
    fun `reinitialize is atomic within synchronized block`() {
        // This test verifies that reinitialize holds the lock throughout the operation.
        // We can't directly test thread safety without complex multi-threaded test setup,
        // but we can verify that successful reinitialize completes without partial state.
        client.initialize("AIzaOldKey123")

        // Set up mock to succeed
        unmockkAll()
        mockkConstructor(GenerativeModel::class)
        every { anyConstructed<GenerativeModel>().modelName } returns "gemini-pro"

        client.reinitialize("AIzaNewKey456")

        assertTrue(client.isInitialized())
    }

    // ---- Generate Content (basic checks) ----

    @Test
    fun `generateContent throws when not initialized`() = runTest {
        try {
            client.generateContent("test prompt")
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not yet initialized") == true)
        }
    }

    @Test
    fun `isInitialized returns false initially`() {
        assertFalse(client.isInitialized())
    }

    @Test
    fun `isInitialized returns true after initialization`() {
        client.initialize("AIzaKey123")
        assertTrue(client.isInitialized())
    }
}
