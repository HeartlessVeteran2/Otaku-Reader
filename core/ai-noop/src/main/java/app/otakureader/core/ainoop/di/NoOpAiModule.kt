package app.otakureader.core.ainoop.di

import app.otakureader.core.ainoop.NoOpAiRepository
import app.otakureader.core.ainoop.NoOpCategorizationRepository
import app.otakureader.core.ainoop.NoOpChapterSummaryRepository
import app.otakureader.core.ainoop.NoOpRecommendationRepository
import app.otakureader.core.ainoop.NoOpSfxTranslationRepository
import app.otakureader.core.ainoop.NoOpSmartSearchCacheRepository
import app.otakureader.core.ainoop.NoOpSourceIntelligenceRepository
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.CategorizationRepository
import app.otakureader.domain.repository.ChapterSummaryRepository
import app.otakureader.domain.repository.RecommendationRepository
import app.otakureader.domain.repository.SfxTranslationRepository
import app.otakureader.domain.repository.SmartSearchCacheRepository
import app.otakureader.domain.repository.SourceIntelligenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for FOSS builds that provides no-op AI repository implementations.
 *
 * In a FOSS product flavor, include `:core:ai-noop` and install this module
 * instead of the real `AiModule` + `RepositoryModule`'s AI bindings.
 *
 * **Duplicate binding prevention**: This module should never be installed alongside
 * [AiRepositoryModule] (the real implementation in `data/src/full/`). The flavor
 * separation in build.gradle.kts enforces this: `foss` flavor depends on
 * `:core:ai-noop` but NOT `:core:ai`, while `full` flavor depends on `:core:ai`
 * but NOT `:core:ai-noop`. If both modules were accidentally included, Hilt would
 * report a duplicate binding error at compile time, preventing a broken build from
 * being created.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoOpAiModule {

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: NoOpAiRepository): AiRepository

    @Binds
    @Singleton
    abstract fun bindRecommendationRepository(impl: NoOpRecommendationRepository): RecommendationRepository

    @Binds
    @Singleton
    abstract fun bindSfxTranslationRepository(impl: NoOpSfxTranslationRepository): SfxTranslationRepository

    @Binds
    @Singleton
    abstract fun bindChapterSummaryRepository(impl: NoOpChapterSummaryRepository): ChapterSummaryRepository

    @Binds
    @Singleton
    abstract fun bindSourceIntelligenceRepository(impl: NoOpSourceIntelligenceRepository): SourceIntelligenceRepository

    @Binds
    @Singleton
    abstract fun bindSmartSearchCacheRepository(impl: NoOpSmartSearchCacheRepository): SmartSearchCacheRepository

    @Binds
    @Singleton
    abstract fun bindCategorizationRepository(impl: NoOpCategorizationRepository): CategorizationRepository
}
