package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/** Progress toward daily and weekly reading goals. */
@Immutable
data class ReadingGoal(
    val dailyGoal: Int = 0,
    val dailyProgress: Int = 0,
    val weeklyGoal: Int = 0,
    val weeklyProgress: Int = 0
)
