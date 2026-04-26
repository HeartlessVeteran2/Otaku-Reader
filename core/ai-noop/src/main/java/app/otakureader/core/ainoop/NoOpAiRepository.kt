package app.otakureader.core.ainoop

import app.otakureader.domain.repository.AiRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op implementation of [AiRepository] used in FOSS builds that exclude the
 * Gemini AI SDK.
 *
 * All methods return graceful failures or indicate that the service is unavailable,
 * so callers guarded by [AiFeatureGate] will degrade without crashing.
 *
 * To use this in a FOSS flavor:
 * 1. Depend on `:core:ai-noop` instead of `:core:ai` in the flavor's dependency block.
 * 2. Install [NoOpAiModule] instead of the real `AiModule`.
 * 3. Replace the `bindAiRepository` binding in `RepositoryModule` with one that binds
 *    [NoOpAiRepository].
 */
@Singleton
class NoOpAiRepository @Inject constructor() : AiRepository {

    override suspend fun generateContent(prompt: String): Result<String> =
        Result.failure(UnsupportedOperationException("AI is not available in this build."))

    override suspend fun generateContentWithImage(
        imageBytes: ByteArray,
        prompt: String,
    ): Result<String> =
        Result.failure(UnsupportedOperationException("AI is not available in this build."))

    override suspend fun isAvailable(): Boolean = false

    override suspend fun initialize(apiKey: String) {
        // No-op: there is no AI client to initialize.
    }

    override suspend fun clearApiKey() {
        // No-op: there is no AI client to reset.
    }
}
