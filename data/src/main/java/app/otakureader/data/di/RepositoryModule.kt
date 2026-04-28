package app.otakureader.data.di

import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.FeedRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.OpdsRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.StatisticsRepository
import app.otakureader.data.opds.OpdsRepositoryImpl
import app.otakureader.data.repository.CategoryRepositoryImpl
import app.otakureader.data.repository.ChapterRepositoryImpl
import app.otakureader.data.repository.DownloadRepositoryImpl
import app.otakureader.data.repository.FeedRepositoryImpl
import app.otakureader.data.repository.MangaRepositoryImpl
import app.otakureader.data.repository.ReaderSettingsRepository
import app.otakureader.data.loader.PageLoader as PageLoaderImpl
import app.otakureader.data.history.WorkManagerHistoryScheduler
import app.otakureader.domain.history.ReadingHistoryScheduler
import app.otakureader.domain.loader.PageLoader
import app.otakureader.domain.repository.ReaderSettingsRepository as ReaderSettingsRepositoryInterface
import app.otakureader.data.repository.SourceRepositoryImpl
import app.otakureader.data.repository.StatisticsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module binding all non-AI repositories.
 *
 * AI-related repositories were removed in Phase 0 (AI extraction).
 * They now live in the companion Otaku-Reader-AI module.
 */
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
    abstract fun bindFeedRepository(
        impl: FeedRepositoryImpl
    ): FeedRepository

    @Binds
    abstract fun bindReaderSettingsRepository(
        impl: ReaderSettingsRepository
    ): ReaderSettingsRepositoryInterface

    @Binds
    abstract fun bindPageLoader(
        impl: PageLoaderImpl
    ): PageLoader

    @Binds
    abstract fun bindReadingHistoryScheduler(
        impl: WorkManagerHistoryScheduler
    ): ReadingHistoryScheduler

    @Binds
    abstract fun bindSourceRepository(
        impl: SourceRepositoryImpl
    ): SourceRepository
}
