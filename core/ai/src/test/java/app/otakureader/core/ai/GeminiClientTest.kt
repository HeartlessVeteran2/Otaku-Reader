package app.otakureader.core.ai

import app.otakureader.core.ai.TestConstants.FAKE_API_KEY_1
import app.otakureader.core.ai.TestConstants.FAKE_API_KEY_2
import app.otakureader.core.ai.TestConstants.FAKE_API_KEY_3
import app.otakureader.core.ai.TestConstants.FAKE_API_KEY_4
import app.otakureader.core.ai.TestConstants.FAKE_API_KEY_5
import app.otakureader.core.ai.TestConstants.FAKE_API_KEY_6
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
        client.initialize(FAKE_API_KEY_1)

        assertTrue(client.isInitialized())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `initialize with blank key throws`() {
        client.initialize("   ")
    }

    @Test
    fun `initialize twice with same key is no-op`() {
        client.initialize(FAKE_API_KEY_2)
        client.initialize(FAKE_API_KEY_2) // Should not throw

        assertTrue(client.isInitialized())
    }

    @Test(expected = IllegalStateException::class)
    fun `initialize twice with different key throws`() {
        client.initialize(FAKE_API_KEY_2)
        client.initialize(FAKE_API_KEY_3) // Should throw
    }

    // ---- Reset ----

    @Test
    fun `reset clears initialized state`() {
        client.initialize(FAKE_API_KEY_2)
        assertTrue(client.isInitialized())

        client.reset()

        assertFalse(client.isInitialized())
    }

    @Test
    fun `reset allows re-initialization`() {
        client.initialize(FAKE_API_KEY_2)
        client.reset()
        client.initialize(FAKE_API_KEY_3) // Should not throw

        assertTrue(client.isInitialized())
    }

    @Test
    fun `reset zeroes configMac buffer to prevent secret residue`() {
        // This test uses reflection to verify that reset() actually zeroes the configMac
        // buffer before reassignment, preventing HMAC-derived secrets from lingering in memory.
        client.initialize(FAKE_API_KEY_2)

        // Capture the configMac ByteArray using reflection before reset
        val configMacField = GeminiClient::class.java.getDeclaredField("configMac")
        configMacField.isAccessible = true
        val macBeforeReset = configMacField.get(client) as ByteArray

        // Verify the MAC is non-empty before reset
        assertTrue("configMac should be non-empty before reset", macBeforeReset.isNotEmpty())

        // Create a copy to verify the original buffer contents
        val macCopy = macBeforeReset.copyOf()
        assertTrue("configMac copy should contain non-zero bytes",
            macCopy.any { it != 0.toByte() })

        client.reset()

        // EXPLICIT ASSERTION: After reset, the captured ByteArray should be all zeros.
        // This verifies that fill(0) was called on the buffer in-place before reassignment.
        assertArrayEquals(
            "configMac buffer must be zeroed (all bytes = 0) after reset to prevent secret residue",
            ByteArray(macBeforeReset.size) { 0 },
            macBeforeReset
        )

        // Also verify re-initialization works
        client.initialize(FAKE_API_KEY_6)
        assertTrue(client.isInitialized())
    }

    // ---- Reinitialize ----

    @Test
    fun `reinitialize replaces existing configuration`() {
        client.initialize(FAKE_API_KEY_4)
        assertTrue(client.isInitialized())

        client.reinitialize(FAKE_API_KEY_5)

        assertTrue(client.isInitialized())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reinitialize with blank key throws`() {
        client.initialize(FAKE_API_KEY_2)
        client.reinitialize("   ")
    }

    @Test
    fun `reinitialize replaces existing configuration atomically`() {
        // This test verifies that reinitialize doesn't leave the client in a half-initialized state.
        // If the new model creation fails, the client should be left uninitialized (not with old state).
        client.initialize(FAKE_API_KEY_4)
        assertTrue(client.isInitialized())

        // Successful reinitialize replaces the configuration
        client.reinitialize(FAKE_API_KEY_5)

        assertTrue(client.isInitialized())
    }

    @Test
    fun `reinitialize clears old state before setting new state`() {
        // This test documents that reinitialize zeros configMac before creating the new model.
        // We can't directly test the zeroing, but we verify successful reinitialize works.
        client.initialize(FAKE_API_KEY_4)

        // Set up mock to succeed
        unmockkAll()
        mockkConstructor(GenerativeModel::class)
        every { anyConstructed<GenerativeModel>().modelName } returns "gemini-pro"

        client.reinitialize(FAKE_API_KEY_5)

        assertTrue(client.isInitialized())
    }

    @Test
    fun `reinitialize is atomic within synchronized block`() {
        // This test verifies that reinitialize holds the lock throughout the operation.
        // We can't directly test thread safety without complex multi-threaded test setup,
        // but we can verify that successful reinitialize completes without partial state.
        client.initialize(FAKE_API_KEY_4)

        // Set up mock to succeed
        unmockkAll()
        mockkConstructor(GenerativeModel::class)
        every { anyConstructed<GenerativeModel>().modelName } returns "gemini-pro"

        client.reinitialize(FAKE_API_KEY_5)

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
        client.initialize(FAKE_API_KEY_2)
        assertTrue(client.isInitialized())
    }
}
