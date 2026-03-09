package app.otakureader.data.di

import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.usecase.DeleteChapterUseCase
import app.otakureader.domain.usecase.GetChaptersUseCase
import app.otakureader.domain.usecase.GetHistoryUseCase
import app.otakureader.domain.usecase.GetLibraryUseCase
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

    @Provides
    fun provideDeleteChapterUseCase(downloadRepository: DownloadRepository): DeleteChapterUseCase =
        DeleteChapterUseCase(downloadRepository)
}
