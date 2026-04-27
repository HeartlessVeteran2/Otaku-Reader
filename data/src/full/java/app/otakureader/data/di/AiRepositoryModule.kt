package app.otakureader.data.di

import app.otakureader.data.repository.AiRepositoryImpl
import app.otakureader.data.repository.CategorizationRepositoryImpl
import app.otakureader.data.repository.ChapterSummaryRepositoryImpl
import app.otakureader.data.repository.RecommendationRepositoryImpl
import app.otakureader.data.repository.SfxTranslationRepositoryImpl
import app.otakureader.data.repository.SmartSearchCacheRepositoryImpl
import app.otakureader.data.repository.SourceIntelligenceRepositoryImpl
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
 * Hilt module that binds the real AI implementations in `full` builds.
 *
 * This module lives in the `full` flavor source set so it is excluded from FOSS
 * builds. FOSS builds get no-op bindings from `:core:ai-noop`.
 *
 * **Duplicate binding prevention**: The flavor separation ensures this module and
 * the FOSS module are never both compiled into the same build.
 *
 * AI-adjacent repositories are bound here (not in RepositoryModule) so that the
 * FOSS flavor can bind no-op stubs via NoOpAiModule without creating duplicates.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAiRepository(
        impl: AiRepositoryImpl
    ): AiRepository

    @Binds
    @Singleton
    abstract fun bindRecommendationRepository(
        impl: RecommendationRepositoryImpl
    ): RecommendationRepository

    @Binds
    @Singleton
    abstract fun bindCategorizationRepository(
        impl: CategorizationRepositoryImpl
    ): CategorizationRepository

    @Binds
    @Singleton
    abstract fun bindSfxTranslationRepository(
        impl: SfxTranslationRepositoryImpl
    ): SfxTranslationRepository

    @Binds
    @Singleton
    abstract fun bindChapterSummaryRepository(
        impl: ChapterSummaryRepositoryImpl
    ): ChapterSummaryRepository

    @Binds
    @Singleton
    abstract fun bindSourceIntelligenceRepository(
        impl: SourceIntelligenceRepositoryImpl
    ): SourceIntelligenceRepository

    @Binds
    @Singleton
    abstract fun bindSmartSearchCacheRepository(
        impl: SmartSearchCacheRepositoryImpl
    ): SmartSearchCacheRepository
}
