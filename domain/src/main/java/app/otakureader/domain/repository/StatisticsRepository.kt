package app.otakureader.domain.repository

import app.otakureader.domain.model.ReadingGoal
import app.otakureader.domain.model.ReadingStats
import kotlinx.coroutines.flow.Flow

interface StatisticsRepository {
    fun getReadingStats(): Flow<ReadingStats>
    fun getReadingGoalProgress(dailyGoal: Int, weeklyGoal: Int): Flow<ReadingGoal>
}
