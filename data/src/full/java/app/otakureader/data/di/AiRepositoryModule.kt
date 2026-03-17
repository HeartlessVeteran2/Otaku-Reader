package app.otakureader.data.di

import app.otakureader.data.repository.AiRepositoryImpl
import app.otakureader.domain.repository.AiRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the real [AiRepository] implementation in `full` builds.
 *
 * This module lives in the `full` flavor source set so it is excluded from FOSS
 * builds. FOSS builds get their [AiRepository] binding from [NoOpAiModule] via
 * `:core:ai-noop`.
 *
 * **Duplicate binding prevention**: The flavor separation ensures this module and
 * [NoOpAiModule] are never both compiled into the same build. The `full` flavor
 * includes `:core:ai` (fullImplementation) but not `:core:ai-noop`, while the `foss`
 * flavor includes `:core:ai-noop` (fossImplementation) but not `:core:ai`. If both
 * modules were accidentally included in the same variant, Hilt would fail at compile
 * time with a duplicate binding error, making this a compile-time safety net.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAiRepository(
        impl: AiRepositoryImpl
    ): AiRepository
}
