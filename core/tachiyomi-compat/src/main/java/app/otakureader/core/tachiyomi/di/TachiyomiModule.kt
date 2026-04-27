package app.otakureader.core.tachiyomi.di

import app.otakureader.core.tachiyomi.health.SourceHealthMonitor
import app.otakureader.domain.usecase.source.GetLatestUpdatesUseCase
import app.otakureader.domain.usecase.source.GetMangaDetailsUseCase
import app.otakureader.domain.usecase.source.GetPopularMangaUseCase
import app.otakureader.domain.usecase.source.GetSourceFiltersUseCase
import app.otakureader.domain.usecase.source.GetSourcesUseCase
import app.otakureader.domain.usecase.source.GlobalSearchUseCase
import app.otakureader.domain.usecase.source.SearchMangaUseCase
import app.otakureader.domain.usecase.library.AddMangaToLibraryUseCase
import app.otakureader.domain.repository.SourceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Tachiyomi compatibility dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object TachiyomiModule {

    // SourceHealthMonitor is provided via @Inject constructor (@Singleton).

    @Provides
    @Singleton
    fun provideSourceHealthMonitor(): SourceHealthMonitor {
        return SourceHealthMonitor()
    }

    @Provides
    fun provideGetSourcesUseCase(
        sourceRepository: SourceRepository
    ): GetSourcesUseCase {
        return GetSourcesUseCase(sourceRepository)
    }

    @Provides
    fun provideGetPopularMangaUseCase(
        sourceRepository: SourceRepository
    ): GetPopularMangaUseCase {
        return GetPopularMangaUseCase(sourceRepository)
    }

    @Provides
    fun provideGetLatestUpdatesUseCase(
        sourceRepository: SourceRepository
    ): GetLatestUpdatesUseCase {
        return GetLatestUpdatesUseCase(sourceRepository)
    }

    @Provides
    fun provideSearchMangaUseCase(
        sourceRepository: SourceRepository
    ): SearchMangaUseCase {
        return SearchMangaUseCase(sourceRepository)
    }

    @Provides
    fun provideGetMangaDetailsUseCase(
        sourceRepository: SourceRepository
    ): GetMangaDetailsUseCase {
        return GetMangaDetailsUseCase(sourceRepository)
    }

    @Provides
    fun provideGlobalSearchUseCase(
        sourceRepository: SourceRepository
    ): GlobalSearchUseCase {
        return GlobalSearchUseCase(sourceRepository)
    }

    @Provides
    fun provideGetSourceFiltersUseCase(
        sourceRepository: SourceRepository
    ): GetSourceFiltersUseCase {
        return GetSourceFiltersUseCase(sourceRepository)
    }
    
    @Provides
    fun provideAddMangaToLibraryUseCase(
        mangaRepository: app.otakureader.domain.repository.MangaRepository
    ): AddMangaToLibraryUseCase {
        return AddMangaToLibraryUseCase(mangaRepository)
    }
}
