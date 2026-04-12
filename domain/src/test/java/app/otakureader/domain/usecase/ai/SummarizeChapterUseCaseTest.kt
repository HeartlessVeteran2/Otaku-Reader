package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.model.ChapterSummary
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.ChapterSummaryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SummarizeChapterUseCaseTest {

    private lateinit var aiRepository: AiRepository
    private lateinit var aiFeatureGate: AiFeatureGate
    private lateinit var chapterSummaryRepository: ChapterSummaryRepository
    private lateinit var useCase: SummarizeChapterUseCase

    @Before
    fun setUp() {
        aiRepository = mockk()
        aiFeatureGate = mockk()
        chapterSummaryRepository = mockk(relaxed = true)
        useCase = SummarizeChapterUseCase(aiRepository, aiFeatureGate, chapterSummaryRepository)
    }

    // ---- feature gate ----

    @Test
    fun `returns failure when SUMMARY_TRANSLATION feature is disabled`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SUMMARY_TRANSLATION) } returns false

        val result = useCase(
            chapterId = 1L,
            mangaId = 10L,
            mangaTitle = "Attack on Titan",
            chapterName = "Chapter 1",
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { aiRepository.generateContent(any()) }
    }

    // ---- cache hit ----

    @Test
    fun `returns cached summary without calling AI`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SUMMARY_TRANSLATION) } returns true
        val cached = ChapterSummary(
            chapterId = 1L,
            mangaId = 10L,
            mangaTitle = "One Piece",
            chapterName = "Chapter 1",
            summary = "Luffy sets sail.",
        )
        coEvery { chapterSummaryRepository.getSummary(1L) } returns cached

        val result = useCase(chapterId = 1L, mangaId = 10L, mangaTitle = "One Piece", chapterName = "Chapter 1")

        assertTrue(result.isSuccess)
        assertEquals("Luffy sets sail.", result.getOrNull()!!.summary)
        coVerify(exactly = 0) { aiRepository.generateContent(any()) }
    }

    // ---- AI call ----

    @Test
    fun `calls AI and caches result when no cached summary exists`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SUMMARY_TRANSLATION) } returns true
        coEvery { chapterSummaryRepository.getSummary(2L) } returns null
        coEvery { aiRepository.generateContent(any()) } returns
            Result.success("Luffy fights the sea king.")

        val result = useCase(chapterId = 2L, mangaId = 10L, mangaTitle = "One Piece", chapterName = "Chapter 2")

        assertTrue(result.isSuccess)
        assertEquals("Luffy fights the sea king.", result.getOrNull()!!.summary)
        coVerify(exactly = 1) { chapterSummaryRepository.saveSummary(any()) }
    }

    @Test
    fun `propagates AI repository failure`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SUMMARY_TRANSLATION) } returns true
        coEvery { chapterSummaryRepository.getSummary(3L) } returns null
        coEvery { aiRepository.generateContent(any()) } returns
            Result.failure(RuntimeException("timeout"))

        val result = useCase(chapterId = 3L, mangaId = 10L, mangaTitle = "Naruto", chapterName = "Chapter 1")

        assertFalse(result.isSuccess)
        coVerify(exactly = 0) { chapterSummaryRepository.saveSummary(any()) }
    }

    @Test
    fun `returns failure when AI returns blank summary`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SUMMARY_TRANSLATION) } returns true
        coEvery { chapterSummaryRepository.getSummary(4L) } returns null
        coEvery { aiRepository.generateContent(any()) } returns Result.success("   ")

        val result = useCase(chapterId = 4L, mangaId = 10L, mangaTitle = "Bleach", chapterName = "Chapter 1")

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `summary includes preceding chapter titles in prompt`() = runTest {
        coEvery { aiFeatureGate.isFeatureAvailable(AiFeature.SUMMARY_TRANSLATION) } returns true
        coEvery { chapterSummaryRepository.getSummary(5L) } returns null

        val capturedPrompts = mutableListOf<String>()
        coEvery { aiRepository.generateContent(capture(capturedPrompts)) } returns
            Result.success("Summary text.")

        useCase(
            chapterId = 5L,
            mangaId = 10L,
            mangaTitle = "Naruto",
            chapterName = "Chapter 5",
            precedingChapterTitles = listOf("Chapter 4", "Chapter 3"),
        )

        assertTrue(capturedPrompts.first().contains("Chapter 4"))
        assertTrue(capturedPrompts.first().contains("Chapter 3"))
    }
}
