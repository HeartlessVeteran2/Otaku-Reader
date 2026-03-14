package app.otakureader.core.tachiyomi.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors the health of manga sources to prevent crashes from dead/failing sources.
 *
 * Inspired by Komikku's SourceHealthMonitor, this class tracks:
 * - Consecutive failure counts per source
 * - Last successful request timestamp
 * - Temporary disablement of repeatedly failing sources
 *
 * Sources that fail repeatedly are marked as unhealthy and can be temporarily disabled
 * to prevent cascading failures and improve app stability.
 */
@Singleton
class SourceHealthMonitor @Inject constructor() {

    companion object {
        /** Number of consecutive failures before marking a source as unhealthy */
        private const val FAILURE_THRESHOLD = 3

        /** Time in milliseconds before retrying an unhealthy source (5 minutes) */
        private const val RETRY_COOLDOWN_MS = 5 * 60 * 1000L

        /** Maximum number of failures to track before permanent disablement */
        private const val MAX_FAILURES = 10
    }

    /**
     * Health status for a source
     */
    data class SourceHealth(
        val sourceId: String,
        val consecutiveFailures: Int = 0,
        val lastSuccessTimestamp: Long = System.currentTimeMillis(),
        val lastFailureTimestamp: Long = 0,
        val lastError: String? = null,
        val isHealthy: Boolean = true,
        val canRetry: Boolean = true
    )

    private val healthMap = ConcurrentHashMap<String, SourceHealth>()
    private val _healthUpdates = MutableStateFlow<Map<String, SourceHealth>>(emptyMap())
    val healthUpdates: StateFlow<Map<String, SourceHealth>> = _healthUpdates.asStateFlow()

    /**
     * Record a successful operation for a source.
     * Resets failure count and marks source as healthy.
     */
    fun recordSuccess(sourceId: String) {
        val current = healthMap[sourceId]
        val updated = SourceHealth(
            sourceId = sourceId,
            consecutiveFailures = 0,
            lastSuccessTimestamp = System.currentTimeMillis(),
            lastFailureTimestamp = current?.lastFailureTimestamp ?: 0,
            lastError = null,
            isHealthy = true,
            canRetry = true
        )
        healthMap[sourceId] = updated
        emitUpdate()
    }

    /**
     * Record a failure for a source.
     * Increments failure count and may mark source as unhealthy.
     */
    fun recordFailure(sourceId: String, error: Throwable) {
        val current = healthMap[sourceId] ?: SourceHealth(sourceId)
        val newFailureCount = (current.consecutiveFailures + 1).coerceAtMost(MAX_FAILURES)
        val now = System.currentTimeMillis()

        val updated = current.copy(
            consecutiveFailures = newFailureCount,
            lastFailureTimestamp = now,
            lastError = error.message ?: error::class.simpleName,
            isHealthy = newFailureCount < FAILURE_THRESHOLD,
            canRetry = newFailureCount < MAX_FAILURES
        )

        healthMap[sourceId] = updated
        emitUpdate()
    }

    /**
     * Check if a source is healthy enough to use.
     *
     * @return true if the source is healthy or past the retry cooldown period
     */
    fun isSourceHealthy(sourceId: String): Boolean {
        val health = healthMap[sourceId] ?: return true

        if (health.isHealthy) {
            return true
        }

        // Allow retry after cooldown period
        val timeSinceLastFailure = System.currentTimeMillis() - health.lastFailureTimestamp
        return timeSinceLastFailure > RETRY_COOLDOWN_MS && health.canRetry
    }

    /**
     * Get the health status of a specific source
     */
    fun getSourceHealth(sourceId: String): SourceHealth {
        return healthMap[sourceId] ?: SourceHealth(sourceId)
    }

    /**
     * Get all sources with health issues
     */
    fun getUnhealthySources(): List<SourceHealth> {
        return healthMap.values.filter { !it.isHealthy }
    }

    /**
     * Reset health data for a specific source
     */
    fun resetSourceHealth(sourceId: String) {
        healthMap.remove(sourceId)
        emitUpdate()
    }

    /**
     * Clear all health data
     */
    fun clearAll() {
        healthMap.clear()
        emitUpdate()
    }

    private fun emitUpdate() {
        _healthUpdates.value = healthMap.toMap()
    }

    /**
     * Get a formatted error message for displaying to users
     */
    fun getHealthMessage(sourceId: String): String? {
        val health = healthMap[sourceId] ?: return null

        if (health.isHealthy) {
            return null
        }

        return buildString {
            append("Source experiencing issues")
            if (health.lastError != null) {
                append(": ${health.lastError}")
            }
            append(" (${health.consecutiveFailures} consecutive failures)")

            if (!health.canRetry) {
                append(". Source has been disabled due to excessive failures.")
            } else if (!isSourceHealthy(sourceId)) {
                val timeLeft = RETRY_COOLDOWN_MS - (System.currentTimeMillis() - health.lastFailureTimestamp)
                val minutesLeft = ((timeLeft + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                append(". Will retry in $minutesLeft minutes.")
            }
        }
    }
}
