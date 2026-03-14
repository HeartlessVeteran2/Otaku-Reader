package app.otakureader.core.ai.di

import app.otakureader.core.ai.GeminiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing AI-related dependencies.
 *
 * This module configures dependency injection for the core:ai module,
 * providing instances of [GeminiClient] to the application.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    /**
     * Provides a singleton instance of [GeminiClient].
     *
     * Note: The client needs to be initialized with an API key
     * before it can be used. This should be done in the application
     * or settings layer.
     *
     * @return A singleton [GeminiClient] instance
     */
    @Provides
    @Singleton
    fun provideGeminiClient(): GeminiClient {
        return GeminiClient()
    }
}
