package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.model.SourceInfo
import app.otakureader.domain.model.SourceScore
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.SourceIntelligenceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScoreSourcesForMangaUseCaseTest {

    private lateinit var aiRepository: AiRepository
    private lateinit var aiFeatureGate: AiFeatureGate
    private lateinit var sourceIntelligenceRepository: SourceIntelligenceRepository
    private lateinit var useCase: ScoreSourcesForMangaUseCase

    private val sources = listOf(
        SourceInfo("en.mangadex", "MangaDex", 120, "Chapter 120", "en"),
        SourceInfo("en.mangaplus", "MangaPlus", 115, "Chapter 115", "en"),
    )

    @Before
    fun setUp() {
        aiRepository = mockk()
        aiFeatureGate = mockk()
        sourceIntelligenceRepository = mockk(relaxed = true)
        useCase = ScoreSourcesForMangaUseCase(aiRepository, aiFeatureGate, sourceIntelligenceRepository)
    }

    // ---- feature gate ----

    @Test
    fun `returns empty list when SOURCE_INTELLIGENCE feature is disabled`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SOURCE_INTELLIGENCE) } returns false

        val result = useCase(mangaId = 1L, mangaTitle = "One Piece", sources = sources)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        coVerify(exactly = 0) { aiRepository.generateContent(any()) }
    }

    @Test
    fun `returns empty list when sources list is empty`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SOURCE_INTELLIGENCE) } returns true

        val result = useCase(mangaId = 1L, mangaTitle = "Naruto", sources = emptyList())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        coVerify(exactly = 0) { aiRepository.generateContent(any()) }
    }

    // ---- cache hit ----

    @Test
    fun `returns cached scores without calling AI`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SOURCE_INTELLIGENCE) } returns true
        val cached = listOf(
            SourceScore("en.mangadex", 1L, 0.9f, 0.8f, 0.95f, 0.88f, "Best source.")
        )
        coEvery { sourceIntelligenceRepository.getScores(1L) } returns cached

        val result = useCase(mangaId = 1L, mangaTitle = "One Piece", sources = sources)

        assertTrue(result.isSuccess)
        assertEquals(cached, result.getOrNull())
        coVerify(exactly = 0) { aiRepository.generateContent(any()) }
    }

    // ---- AI call ----

    @Test
    fun `calls AI and returns sorted scores when cache is empty`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SOURCE_INTELLIGENCE) } returns true
        coEvery { sourceIntelligenceRepository.getScores(2L) } returns emptyList()
        coEvery { aiRepository.generateContent(any()) } returns Result.success(
            "en.mangadex|0.9|0.8|0.95|Top choice for quality scans.\n" +
                "en.mangaplus|0.7|0.9|0.8|Official but lower scan quality."
        )

        val result = useCase(mangaId = 2L, mangaTitle = "Bleach", sources = sources)

        assertTrue(result.isSuccess)
        val scores = result.getOrNull()!!
        assertEquals(2, scores.size)
        // Sorted by overall score descending
        assertTrue(scores[0].overallScore >= scores[1].overallScore)
        coVerify(exactly = 1) { sourceIntelligenceRepository.saveScores(2L, any()) }
    }

    @Test
    fun `returns empty list when AI call fails`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SOURCE_INTELLIGENCE) } returns true
        coEvery { sourceIntelligenceRepository.getScores(3L) } returns emptyList()
        coEvery { aiRepository.generateContent(any()) } returns
            Result.failure(RuntimeException("network error"))

        val result = useCase(mangaId = 3L, mangaTitle = "Naruto", sources = sources)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    // ---- parseAiResponse unit tests ----

    @Test
    fun `parseAiResponse fills default scores for sources not in AI response`() {
        val response = "en.mangadex|0.9|0.8|0.95|Great source."
        val scores = useCase.parseAiResponse(response, mangaId = 1L, sources = sources)

        assertEquals(2, scores.size)
        val mangadex = scores.find { it.sourceId == "en.mangadex" }!!
        assertEquals(0.9f, mangadex.contentQualityScore, 0.001f)

        val mangaplus = scores.find { it.sourceId == "en.mangaplus" }!!
        assertEquals(0.5f, mangaplus.overallScore, 0.001f) // default
    }

    @Test
    fun `parseAiResponse coerces scores to 0-1 range`() {
        val response = "en.mangadex|1.5|-0.3|0.8|Test."
        val scores = useCase.parseAiResponse(response, mangaId = 1L, sources = sources)

        val mangadex = scores.find { it.sourceId == "en.mangadex" }!!
        assertEquals(1.0f, mangadex.contentQualityScore, 0.001f)
        assertEquals(0.0f, mangadex.updateFrequencyScore, 0.001f)
    }
}
