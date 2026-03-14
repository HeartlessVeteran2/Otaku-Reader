package app.otakureader.core.ai.di

import app.otakureader.core.ai.GeminiClient
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for providing AI-related dependencies.
 *
 * This module configures dependency injection for the core:ai module,
 * providing instances of [GeminiClient] to the application.
 *
 * [GeminiClient] itself is provided via its @Inject constructor, so
 * no explicit @Provides binding is necessary here.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule
