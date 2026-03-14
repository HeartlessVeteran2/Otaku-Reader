package app.otakureader.data.di

import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.OpdsRepository
import app.otakureader.domain.usecase.DeleteChapterUseCase
import app.otakureader.domain.usecase.GetChaptersUseCase
import app.otakureader.domain.usecase.GetHistoryUseCase
import app.otakureader.domain.usecase.GetLibraryUseCase
import app.otakureader.domain.usecase.opds.BrowseOpdsCatalogUseCase
import app.otakureader.domain.usecase.opds.DeleteOpdsServerUseCase
import app.otakureader.domain.usecase.opds.GetOpdsServersUseCase
import app.otakureader.domain.usecase.opds.SaveOpdsServerUseCase
import app.otakureader.domain.usecase.opds.SearchOpdsCatalogUseCase
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

    @Provides
    fun provideGetOpdsServersUseCase(opdsRepository: OpdsRepository): GetOpdsServersUseCase =
        GetOpdsServersUseCase(opdsRepository)

    @Provides
    fun provideSaveOpdsServerUseCase(opdsRepository: OpdsRepository): SaveOpdsServerUseCase =
        SaveOpdsServerUseCase(opdsRepository)

    @Provides
    fun provideDeleteOpdsServerUseCase(opdsRepository: OpdsRepository): DeleteOpdsServerUseCase =
        DeleteOpdsServerUseCase(opdsRepository)

    @Provides
    fun provideBrowseOpdsCatalogUseCase(opdsRepository: OpdsRepository): BrowseOpdsCatalogUseCase =
        BrowseOpdsCatalogUseCase(opdsRepository)

    @Provides
    fun provideSearchOpdsCatalogUseCase(opdsRepository: OpdsRepository): SearchOpdsCatalogUseCase =
        SearchOpdsCatalogUseCase(opdsRepository)
}
