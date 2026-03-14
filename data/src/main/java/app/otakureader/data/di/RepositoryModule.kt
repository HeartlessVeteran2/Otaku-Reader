package app.otakureader.data.di

import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.OpdsRepository
import app.otakureader.domain.repository.StatisticsRepository
import app.otakureader.data.opds.OpdsRepositoryImpl
import app.otakureader.data.repository.AiRepositoryImpl
import app.otakureader.data.repository.CategoryRepositoryImpl
import app.otakureader.data.repository.ChapterRepositoryImpl
import app.otakureader.data.repository.DownloadRepositoryImpl
import app.otakureader.data.repository.MangaRepositoryImpl
import app.otakureader.data.repository.StatisticsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMangaRepository(
        impl: MangaRepositoryImpl
    ): MangaRepository

    @Binds
    abstract fun bindChapterRepository(
        impl: ChapterRepositoryImpl
    ): ChapterRepository

    @Binds
    abstract fun bindDownloadRepository(
        impl: DownloadRepositoryImpl
    ): DownloadRepository

    @Binds
    abstract fun bindCategoryRepository(
        impl: CategoryRepositoryImpl
    ): CategoryRepository

    @Binds
    abstract fun bindStatisticsRepository(
        impl: StatisticsRepositoryImpl
    ): StatisticsRepository

    @Binds
    abstract fun bindOpdsRepository(
        impl: OpdsRepositoryImpl
    ): OpdsRepository

    @Binds
    abstract fun bindAiRepository(
        impl: AiRepositoryImpl
    ): AiRepository
}
