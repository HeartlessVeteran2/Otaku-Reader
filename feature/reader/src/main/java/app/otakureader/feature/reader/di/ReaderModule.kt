package app.otakureader.feature.reader.di

import app.otakureader.feature.reader.repository.ReaderSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * Hilt dependency injection module for the reader feature.
 */
@Module
@InstallIn(ViewModelComponent::class)
object ReaderModule {

    /**
     * Provides the ReaderSettingsRepository.
     * Note: The actual repository is constructor-injected, so this module
     * ensures proper scoping and can provide additional bindings if needed.
     */
    @Provides
    @ViewModelScoped
    fun provideReaderSettingsRepository(repository: ReaderSettingsRepository): ReaderSettingsRepository {
        return repository
    }
}
