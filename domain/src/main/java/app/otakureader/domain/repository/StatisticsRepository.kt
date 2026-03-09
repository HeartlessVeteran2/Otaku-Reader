package app.otakureader.domain.repository

import app.otakureader.domain.model.ReadingStats
import kotlinx.coroutines.flow.Flow

interface StatisticsRepository {
    fun getReadingStats(): Flow<ReadingStats>
}
