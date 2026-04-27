package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.OcrTranslationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslateOcrPageUseCaseTest {

    private lateinit var aiRepository: AiRepository
    private lateinit var aiFeatureGate: AiFeatureGate
    private lateinit var ocrRepository: OcrTranslationRepository
    private lateinit var useCase: TranslateOcrPageUseCase

    private val sampleBytes = ByteArray(8) { it.toByte() }

    @Before
    fun setUp() {
        aiRepository = mockk()
        aiFeatureGate = mockk()
        ocrRepository = mockk(relaxed = true)
        useCase = TranslateOcrPageUseCase(aiRepository, aiFeatureGate, ocrRepository)
    }

    // ---- feature gate ----

    @Test
    fun `returns empty list when OCR_TRANSLATION feature is disabled`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.OCR_TRANSLATION) } returns false

        val result = useCase(chapterId = 1L, pageIndex = 0, imageBytes = sampleBytes)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        coVerify(exactly = 0) { aiRepository.generateContentWithImage(any(), any()) }
    }

    // ---- cache hit ----

    @Test
    fun `returns cached translations without calling AI`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.OCR_TRANSLATION) } returns true
        val cached = listOf(
            app.otakureader.domain.model.OcrTranslation(
                pageIndex = 0,
                originalText = "こんにちは",
                translatedText = "Hello",
                confidence = 0.9f,
                positionHint = "top-right",
            )
        )
        coEvery { ocrRepository.getTranslations(1L, 0) } returns cached

        val result = useCase(chapterId = 1L, pageIndex = 0, imageBytes = sampleBytes)

        assertTrue(result.isSuccess)
        assertEquals(cached, result.getOrNull())
        coVerify(exactly = 0) { aiRepository.generateContentWithImage(any(), any()) }
    }

    @Test
    fun `returns empty list without calling AI when cache has empty entry (no text page)`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.OCR_TRANSLATION) } returns true
        coEvery { ocrRepository.getTranslations(1L, 0) } returns emptyList()

        val result = useCase(chapterId = 1L, pageIndex = 0, imageBytes = sampleBytes)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        coVerify(exactly = 0) { aiRepository.generateContentWithImage(any(), any()) }
    }

    // ---- empty input ----

    @Test
    fun `returns empty list when imageBytes is empty`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.OCR_TRANSLATION) } returns true
        coEvery { ocrRepository.getTranslations(1L, 0) } returns null

        val result = useCase(chapterId = 1L, pageIndex = 0, imageBytes = ByteArray(0))

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        coVerify(exactly = 0) { aiRepository.generateContentWithImage(any(), any()) }
    }

    // ---- AI call and parse ----

    @Test
    fun `calls AI and parses valid response when cache miss`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.OCR_TRANSLATION) } returns true
        coEvery { ocrRepository.getTranslations(1L, 0) } returns null
        coEvery { aiRepository.generateContentWithImage(sampleBytes, any()) } returns
            Result.success("こんにちは|||Hello|||0.95|||top-right\n世界|||World|||0.80|||bottom-centre")

        val result = useCase(chapterId = 1L, pageIndex = 0, imageBytes = sampleBytes)

        assertTrue(result.isSuccess)
        val translations = result.getOrNull()!!
        assertEquals(2, translations.size)
        assertEquals("こんにちは", translations[0].originalText)
        assertEquals("Hello", translations[0].translatedText)
        assertEquals(0.95f, translations[0].confidence, 0.001f)
        assertEquals("top-right", translations[0].positionHint)
        coVerify(exactly = 1) { ocrRepository.saveTranslations(1L, 0, translations) }
    }

    @Test
    fun `returns empty list when AI responds with NONE`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.OCR_TRANSLATION) } returns true
        coEvery { ocrRepository.getTranslations(1L, 0) } returns null
        coEvery { aiRepository.generateContentWithImage(sampleBytes, any()) } returns Result.success("NONE")

        val result = useCase(chapterId = 1L, pageIndex = 0, imageBytes = sampleBytes)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        coVerify(exactly = 1) { ocrRepository.saveTranslations(1L, 0, emptyList()) }
    }

    @Test
    fun `propagates failure when AI call fails`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.OCR_TRANSLATION) } returns true
        coEvery { ocrRepository.getTranslations(1L, 0) } returns null
        coEvery { aiRepository.generateContentWithImage(sampleBytes, any()) } returns
            Result.failure(RuntimeException("network error"))

        val result = useCase(chapterId = 1L, pageIndex = 0, imageBytes = sampleBytes)

        assertTrue(result.isFailure)
        assertEquals("network error", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { ocrRepository.saveTranslations(any(), any(), any()) }
    }

    // ---- parseAiResponse unit tests ----

    @Test
    fun `parseAiResponse skips malformed lines`() {
        val response = "only_one_part\nこんにちは|||Hello|||0.9|||top"
        val result = useCase.parseAiResponse(response, pageIndex = 2)

        assertEquals(1, result.size)
        assertEquals("こんにちは", result[0].originalText)
        assertEquals(2, result[0].pageIndex)
    }

    @Test
    fun `parseAiResponse coerces confidence to 0-1 range`() {
        val response = "A|||B|||1.5|||top"
        val result = useCase.parseAiResponse(response, pageIndex = 0)

        assertEquals(1.0f, result[0].confidence, 0.001f)
    }

    @Test
    fun `parseAiResponse returns empty list for NONE response`() {
        val result = useCase.parseAiResponse("NONE", pageIndex = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseAiResponse treats position hint as optional`() {
        val response = "A|||B|||0.9"
        val result = useCase.parseAiResponse(response, pageIndex = 0)

        assertEquals(1, result.size)
        assertEquals(null, result[0].positionHint)
    }

    @Test
    fun `parseAiResponse preserves pipes inside position hint field`() {
        val response = "A|||B|||0.9|||hint|with|pipes"
        val result = useCase.parseAiResponse(response, pageIndex = 0)

        assertEquals(1, result.size)
        assertEquals("hint|with|pipes", result[0].positionHint)
    }
}
