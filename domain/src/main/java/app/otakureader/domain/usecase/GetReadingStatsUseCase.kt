package app.otakureader.domain.usecase

import app.otakureader.domain.model.ReadingStats
import app.otakureader.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetReadingStatsUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {
    operator fun invoke(): Flow<ReadingStats> = statisticsRepository.getReadingStats()
}
