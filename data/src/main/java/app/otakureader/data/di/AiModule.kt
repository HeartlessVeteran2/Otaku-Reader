package app.otakureader.data.di

import app.otakureader.data.loader.AssetsPromptLoader
import app.otakureader.data.repository.AiFeatureGateImpl
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.repository.PromptLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for AI-related dependencies.
 * Consolidates AI feature gate and prompt loader bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    /**
     * Binds [AiFeatureGateImpl] to [AiFeatureGate] interface.
     */
    @Binds
    @Singleton
    abstract fun bindAiFeatureGate(
        impl: AiFeatureGateImpl
    ): AiFeatureGate

    /**
     * Binds [AssetsPromptLoader] to [PromptLoader] interface.
     */
    @Binds
    @Singleton
    abstract fun bindPromptLoader(
        impl: AssetsPromptLoader
    ): PromptLoader
}
