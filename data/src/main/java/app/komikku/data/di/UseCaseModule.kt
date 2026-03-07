package app.komikku.data.di

import app.komikku.domain.repository.ChapterRepository
import app.komikku.domain.repository.MangaRepository
import app.komikku.domain.usecase.GetChaptersUseCase
import app.komikku.domain.usecase.GetHistoryUseCase
import app.komikku.domain.usecase.GetLibraryUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module that provides use case instances.
 * Use cases are provided (not bound) because they are not interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideGetLibraryUseCase(mangaRepository: MangaRepository): GetLibraryUseCase =
        GetLibraryUseCase(mangaRepository)

    @Provides
    fun provideGetChaptersUseCase(chapterRepository: ChapterRepository): GetChaptersUseCase =
        GetChaptersUseCase(chapterRepository)

    @Provides
    fun provideGetHistoryUseCase(chapterRepository: ChapterRepository): GetHistoryUseCase =
        GetHistoryUseCase(chapterRepository)
}
