package app.otakureader.domain.model

/** Progress toward daily and weekly reading goals. */
data class ReadingGoal(
    val dailyGoal: Int = 0,
    val dailyProgress: Int = 0,
    val weeklyGoal: Int = 0,
    val weeklyProgress: Int = 0
)
