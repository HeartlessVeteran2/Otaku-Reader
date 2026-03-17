package app.otakureader.core.ainoop.di

import app.otakureader.core.ainoop.NoOpAiRepository
import app.otakureader.domain.repository.AiRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for FOSS builds that provides the no-op [AiRepository].
 *
 * In a FOSS product flavor, include `:core:ai-noop` and install this module
 * instead of the real `AiModule` + `RepositoryModule`'s `bindAiRepository`.
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
    abstract fun bindAiRepository(
        impl: NoOpAiRepository
    ): AiRepository
}
