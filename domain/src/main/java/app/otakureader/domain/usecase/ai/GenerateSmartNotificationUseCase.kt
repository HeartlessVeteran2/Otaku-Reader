package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.repository.AiRepository
import javax.inject.Inject

class GenerateSmartNotificationUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val aiFeatureGate: AiFeatureGate
) {
    /**
     * Generate a personalized reading reminder message.
     *
     * @param chaptersToday Chapters read today.
     * @param dailyGoal User's daily chapter goal (0 = no goal).
     * @param currentStreak Current reading streak in days.
     * @return Personalized notification text, or null if AI is unavailable.
     */
    suspend operator fun invoke(
        chaptersToday: Int,
        dailyGoal: Int,
        currentStreak: Int
    ): String? {
        if (!aiFeatureGate.isFeatureAvailable(AiFeature.SMART_NOTIFICATIONS)) return null

        val prompt = buildPrompt(chaptersToday, dailyGoal, currentStreak)
        return aiRepository.generateContent(prompt).getOrNull()
            ?.trim()
            ?.lines()
            ?.firstOrNull { it.isNotBlank() }
            ?.take(120)
    }

    private fun buildPrompt(chaptersToday: Int, dailyGoal: Int, currentStreak: Int): String {
        val goalContext = when {
            dailyGoal > 0 && chaptersToday >= dailyGoal -> "already met today's goal of $dailyGoal chapters"
            dailyGoal > 0 -> "$chaptersToday of $dailyGoal chapters done today"
            else -> "no specific daily goal set"
        }
        val streakContext = if (currentStreak > 0) "$currentStreak-day reading streak" else "no current streak"
        return """
            You are a friendly reading coach for a manga app. Write a single short, encouraging
            push notification (under 120 characters) to remind the user to read manga.

            Context: $goalContext, $streakContext.

            Vary the message — be warm, motivating, and fun. No emoji. One sentence only.
        """.trimIndent()
    }
}
