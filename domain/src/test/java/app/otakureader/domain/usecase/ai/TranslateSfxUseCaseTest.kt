package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.SfxTranslationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslateSfxUseCaseTest {

    private lateinit var aiRepository: AiRepository
    private lateinit var aiFeatureGate: AiFeatureGate
    private lateinit var sfxTranslationRepository: SfxTranslationRepository
    private lateinit var useCase: TranslateSfxUseCase

    @Before
    fun setUp() {
        aiRepository = mockk()
        aiFeatureGate = mockk()
        sfxTranslationRepository = mockk(relaxed = true)
        useCase = TranslateSfxUseCase(aiRepository, aiFeatureGate, sfxTranslationRepository)
    }

    // ---- feature gate ----

    @Test
    fun `returns empty list when SFX_TRANSLATION feature is disabled`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SFX_TRANSLATION) } returns false

        val result = useCase(chapterId = 1L, pageIndex = 0, pageImageUrl = "https://example.com/page.jpg")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        coVerify(exactly = 0) { aiRepository.generateContent(any()) }
    }

    // ---- cache hit (non-empty) ----

    @Test
    fun `returns cached translations without calling AI`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SFX_TRANSLATION) } returns true
        val cached = listOf(
            app.otakureader.domain.model.SfxTranslation(0, "ドカン", "BOOM", 0.9f)
        )
        coEvery { sfxTranslationRepository.getTranslations(1L, 0) } returns cached

        val result = useCase(chapterId = 1L, pageIndex = 0, pageImageUrl = "https://example.com/page.jpg")

        assertTrue(result.isSuccess)
        assertEquals(cached, result.getOrNull())
        coVerify(exactly = 0) { aiRepository.generateContent(any()) }
    }

    @Test
    fun `returns empty list without calling AI when cache has empty entry (no SFX page)`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SFX_TRANSLATION) } returns true
        // Empty list in cache = page was previously analyzed and has no SFX
        coEvery { sfxTranslationRepository.getTranslations(1L, 0) } returns emptyList()

        val result = useCase(chapterId = 1L, pageIndex = 0, pageImageUrl = "https://example.com/page.jpg")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        coVerify(exactly = 0) { aiRepository.generateContent(any()) }
    }

    // ---- AI call and parse ----

    @Test
    fun `calls AI and parses valid response when cache is null (true cache miss)`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SFX_TRANSLATION) } returns true
        coEvery { sfxTranslationRepository.getTranslations(1L, 0) } returns null
        coEvery { aiRepository.generateContent(any()) } returns
            Result.success("ドカン|BOOM|0.95|top-left\nバキ|CRACK|0.80|bottom-right")

        val result = useCase(chapterId = 1L, pageIndex = 0, pageImageUrl = "https://example.com/page.jpg")

        assertTrue(result.isSuccess)
        val translations = result.getOrNull()!!
        assertEquals(2, translations.size)
        assertEquals("ドカン", translations[0].originalText)
        assertEquals("BOOM", translations[0].translatedText)
        assertEquals(0.95f, translations[0].confidence, 0.001f)
        assertEquals("top-left", translations[0].positionHint)
        coVerify(exactly = 1) { sfxTranslationRepository.saveTranslations(1L, 0, translations) }
    }

    @Test
    fun `returns empty list when AI responds with NONE`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SFX_TRANSLATION) } returns true
        coEvery { sfxTranslationRepository.getTranslations(1L, 0) } returns null
        coEvery { aiRepository.generateContent(any()) } returns Result.success("NONE")

        val result = useCase(chapterId = 1L, pageIndex = 0, pageImageUrl = "https://example.com/page.jpg")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `returns empty list when AI call fails`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SFX_TRANSLATION) } returns true
        coEvery { sfxTranslationRepository.getTranslations(1L, 0) } returns null
        coEvery { aiRepository.generateContent(any()) } returns
            Result.failure(RuntimeException("network error"))

        val result = useCase(chapterId = 1L, pageIndex = 0, pageImageUrl = "https://example.com/page.jpg")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    // ---- parseAiResponse unit tests ----

    @Test
    fun `parseAiResponse skips malformed lines`() {
        val response = "only_one_part\nドカン|BOOM|0.9|left"
        val result = useCase.parseAiResponse(response, pageIndex = 2)

        assertEquals(1, result.size)
        assertEquals("ドカン", result[0].originalText)
        assertEquals(2, result[0].pageIndex)
    }

    @Test
    fun `parseAiResponse coerces confidence to 0-1 range`() {
        val response = "A|B|1.5|top"
        val result = useCase.parseAiResponse(response, pageIndex = 0)

        assertEquals(1.0f, result[0].confidence, 0.001f)
    }

    @Test
    fun `parseAiResponse returns empty list for NONE response`() {
        val result = useCase.parseAiResponse("NONE", pageIndex = 0)
        assertTrue(result.isEmpty())
    }
}
