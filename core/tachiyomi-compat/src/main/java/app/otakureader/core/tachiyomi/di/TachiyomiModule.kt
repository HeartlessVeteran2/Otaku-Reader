package app.otakureader.core.tachiyomi.di

import android.content.Context
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.tachiyomi.repository.SourceRepositoryImpl
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.source.GetLatestUpdatesUseCase
import app.otakureader.domain.usecase.source.GetMangaDetailsUseCase
import app.otakureader.domain.usecase.source.GetPopularMangaUseCase
import app.otakureader.domain.usecase.source.GetSourcesUseCase
import app.otakureader.domain.usecase.source.GlobalSearchUseCase
import app.otakureader.domain.usecase.source.SearchMangaUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Tachiyomi compatibility dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object TachiyomiModule {

    @Provides
    @Singleton
    fun provideSourceRepository(
        @ApplicationContext context: Context,
        localSourcePreferences: LocalSourcePreferences
    ): SourceRepository {
        return SourceRepositoryImpl(context, localSourcePreferences)
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
}
