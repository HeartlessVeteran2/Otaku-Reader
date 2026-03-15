package app.otakureader.data.repository

import app.otakureader.core.ai.GeminiClient
import com.google.ai.client.generativeai.type.GenerateContentResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiRepositoryImplTest {

    private lateinit var geminiClient: GeminiClient
    private lateinit var repository: AiRepositoryImpl

    @Before
    fun setUp() {
        geminiClient = mockk()
        repository = AiRepositoryImpl(geminiClient)
    }

    // ---- generateContent: success ----

    @Test
    fun `generateContent returns success when response has valid text`() = runTest {
        val response = mockk<GenerateContentResponse>()
        every { geminiClient.isInitialized() } returns true
        every { response.text } returns "A valid summary"
        coEvery { geminiClient.generateContent(any()) } returns response

        val result = repository.generateContent("describe this manga")

        assertTrue(result.isSuccess)
        assertEquals("A valid summary", result.getOrNull())
    }

    // ---- generateContent: blank / whitespace-only response ----

    @Test
    fun `generateContent returns failure when response text is empty`() = runTest {
        val response = mockk<GenerateContentResponse>()
        every { geminiClient.isInitialized() } returns true
        every { response.text } returns ""
        coEvery { geminiClient.generateContent(any()) } returns response

        val result = repository.generateContent("describe this manga")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("empty"))
    }

    @Test
    fun `generateContent returns failure when response text is whitespace only`() = runTest {
        val response = mockk<GenerateContentResponse>()
        every { geminiClient.isInitialized() } returns true
        every { response.text } returns "   \n\t  "
        coEvery { geminiClient.generateContent(any()) } returns response

        val result = repository.generateContent("describe this manga")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("empty"))
    }

    @Test
    fun `generateContent returns failure when response text is null`() = runTest {
        val response = mockk<GenerateContentResponse>()
        every { geminiClient.isInitialized() } returns true
        every { response.text } returns null
        coEvery { geminiClient.generateContent(any()) } returns response

        val result = repository.generateContent("describe this manga")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    // ---- generateContent: not initialized ----

    @Test
    fun `generateContent returns failure when client is not initialized`() = runTest {
        every { geminiClient.isInitialized() } returns false

        val result = repository.generateContent("describe this manga")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("not initialized"))
    }

    // ---- generateContent: timeout ----

    @Test
    fun `generateContent returns failure when request times out`() = runTest {
        every { geminiClient.isInitialized() } returns true
        // Simulate a TimeoutCancellationException the same way the real SDK would throw it
        coEvery { geminiClient.generateContent(any()) } coAnswers {
            // Using a real withTimeout + delay to obtain a genuine TimeoutCancellationException,
            // since its constructor is internal and can't be instantiated directly.
            try {
                kotlinx.coroutines.withTimeout(10L) { kotlinx.coroutines.delay(1_000L) }
                error("unreachable")
            } catch (e: TimeoutCancellationException) {
                throw e
            }
        }

        val result = repository.generateContent("describe this manga")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
        assertTrue(exception!!.message!!.contains("timed out"))
    }

    // ---- generateContent: external cancellation propagates ----

    @Test
    fun `generateContent rethrows CancellationException for external cancellations`() = runTest {
        every { geminiClient.isInitialized() } returns true
        coEvery { geminiClient.generateContent(any()) } throws CancellationException("cancelled externally")

        var threw = false
        try {
            repository.generateContent("describe this manga")
        } catch (e: CancellationException) {
            threw = true
        }

        assertTrue(threw)
    }

    // ---- generateContent: generic exception ----

    @Test
    fun `generateContent wraps generic exception as failure`() = runTest {
        every { geminiClient.isInitialized() } returns true
        coEvery { geminiClient.generateContent(any()) } throws RuntimeException("network error")

        val result = repository.generateContent("describe this manga")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is RuntimeException)
        assertEquals("network error", exception!!.message)
    }

    // ---- isAvailable ----

    @Test
    fun `isAvailable returns true when client is initialized`() = runTest {
        every { geminiClient.isInitialized() } returns true

        assertTrue(repository.isAvailable())
    }

    @Test
    fun `isAvailable returns false when client is not initialized`() = runTest {
        every { geminiClient.isInitialized() } returns false

        assertFalse(repository.isAvailable())
    }
}
