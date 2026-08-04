package app.otakureader.data.di

import app.otakureader.domain.repository.AchievementRepository
import app.otakureader.domain.repository.BookmarkCollectionRepository
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DynamicCategoryRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.EhFavoritesRepository
import app.otakureader.domain.repository.ExtensionManagementRepository
import app.otakureader.domain.repository.FeedRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.OpdsRepository
import app.otakureader.domain.repository.PageBookmarkRepository
import app.otakureader.domain.repository.ReadingListRepository
import app.otakureader.domain.repository.RecommendationRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.StatisticsRepository
import app.otakureader.domain.repository.SyncRepository
import app.otakureader.data.opds.OpdsRepositoryImpl
import app.otakureader.data.eh.EhFavoritesRepositoryImpl
import app.otakureader.data.repository.AchievementRepositoryImpl
import app.otakureader.data.repository.CategoryRepositoryImpl
import app.otakureader.data.repository.ChapterRepositoryImpl
import app.otakureader.data.repository.DataUsageRepositoryImpl
import app.otakureader.data.repository.DownloadRepositoryImpl
import app.otakureader.domain.repository.DataUsageRepository
import app.otakureader.data.repository.FeedRepositoryImpl
import app.otakureader.data.repository.MangaRepositoryImpl
import app.otakureader.data.repository.ReaderSettingsRepository
import app.otakureader.data.loader.PageLoader as PageLoaderImpl
import app.otakureader.data.history.WorkManagerHistoryScheduler
import app.otakureader.data.backup.BackupScheduler as BackupSchedulerImpl
import app.otakureader.data.backup.repository.BackupRepository as BackupRepositoryImpl
import app.otakureader.data.backup.tachiyomi.TachiyomiBackupImporter as TachiyomiBackupImporterImpl
import app.otakureader.data.tracking.TrackManager as TrackManagerImpl
import app.otakureader.data.updater.AppUpdateChecker as AppUpdateCheckerImpl
import app.otakureader.data.worker.CoverRefreshSchedulerImpl
import app.otakureader.data.worker.DownloadFolderMigrationSchedulerImpl
import app.otakureader.data.worker.LibraryUpdateScheduler as LibraryUpdateSchedulerImpl
import app.otakureader.data.worker.ReadingReminderScheduler as ReadingReminderSchedulerImpl
import app.otakureader.domain.backup.BackupRepository
import app.otakureader.domain.backup.BackupScheduler
import app.otakureader.domain.backup.TachiyomiBackupImporter
import app.otakureader.domain.history.ReadingHistoryScheduler
import app.otakureader.domain.loader.PageLoader
import app.otakureader.domain.repository.ReaderSettingsRepository as ReaderSettingsRepositoryInterface
import app.otakureader.data.repository.BookmarkCollectionRepositoryImpl
import app.otakureader.data.repository.PageBookmarkRepositoryImpl
import app.otakureader.data.repository.ReaderCommentRepositoryImpl
import app.otakureader.domain.repository.ReaderCommentRepository
import app.otakureader.data.repository.ReadingListRepositoryImpl
import app.otakureader.data.repository.SourceRepositoryImpl
import app.otakureader.data.repository.StatisticsRepositoryImpl
import app.otakureader.data.repository.DynamicCategoryRepositoryImpl
import app.otakureader.data.repository.RecommendationRepositoryImpl
import app.otakureader.data.repository.UpdateErrorRepositoryImpl
import app.otakureader.data.repository.UpdateRunSummaryRepositoryImpl
import app.otakureader.data.sync.SyncRepositoryImpl
import app.otakureader.data.worker.ExtensionUpdateSchedulerImpl
import app.otakureader.data.worker.SyncSchedulerImpl
import app.otakureader.data.worker.TrackerSyncSchedulerImpl
import app.otakureader.domain.scheduler.CoverRefreshScheduler
import app.otakureader.domain.scheduler.DownloadFolderMigrationScheduler
import app.otakureader.domain.scheduler.ExtensionUpdateScheduler
import app.otakureader.domain.scheduler.LibraryUpdateScheduler
import app.otakureader.domain.scheduler.ReminderScheduler
import app.otakureader.data.metadata.AniListMetadataRepository
import app.otakureader.domain.repository.MangaMetadataRepository
import app.otakureader.domain.repository.UpdateErrorRepository
import app.otakureader.domain.repository.UpdateRunSummaryRepository
import app.otakureader.domain.scheduler.SyncScheduler
import app.otakureader.domain.scheduler.TrackerSyncScheduler
import app.otakureader.domain.tracking.TrackManager
import app.otakureader.domain.updater.AppUpdateChecker
import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module that binds data-layer repository implementations to their domain interfaces. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAchievementRepository(impl: AchievementRepositoryImpl): AchievementRepository

    @Binds
    abstract fun bindMangaRepository(impl: MangaRepositoryImpl): MangaRepository

    @Binds
    abstract fun bindChapterRepository(impl: ChapterRepositoryImpl): ChapterRepository

    @Binds
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindStatisticsRepository(impl: StatisticsRepositoryImpl): StatisticsRepository

    @Binds
    abstract fun bindOpdsRepository(impl: OpdsRepositoryImpl): OpdsRepository

    @Binds
    abstract fun bindFeedRepository(impl: FeedRepositoryImpl): FeedRepository

    @Binds
    abstract fun bindReaderSettingsRepository(impl: ReaderSettingsRepository): ReaderSettingsRepositoryInterface

    @Binds
    abstract fun bindPageLoader(impl: PageLoaderImpl): PageLoader

    @Binds
    abstract fun bindReadingHistoryScheduler(impl: WorkManagerHistoryScheduler): ReadingHistoryScheduler

    @Binds
    abstract fun bindPageBookmarkRepository(impl: PageBookmarkRepositoryImpl): PageBookmarkRepository

    @Binds
    abstract fun bindBookmarkCollectionRepository(impl: BookmarkCollectionRepositoryImpl): BookmarkCollectionRepository

    @Binds
    abstract fun bindReaderCommentRepository(impl: ReaderCommentRepositoryImpl): ReaderCommentRepository

    @Binds
    abstract fun bindSourceRepository(impl: SourceRepositoryImpl): SourceRepository

    @Binds
    abstract fun bindExtensionManagementRepository(impl: SourceRepositoryImpl): ExtensionManagementRepository

    @Binds
    abstract fun bindReadingListRepository(impl: ReadingListRepositoryImpl): ReadingListRepository

    @Binds
    abstract fun bindReminderScheduler(impl: ReadingReminderSchedulerImpl): ReminderScheduler

    @Binds
    abstract fun bindLibraryUpdateScheduler(impl: LibraryUpdateSchedulerImpl): LibraryUpdateScheduler

    @Binds
    abstract fun bindBackupScheduler(impl: BackupSchedulerImpl): BackupScheduler

    @Binds
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    abstract fun bindTachiyomiBackupImporter(impl: TachiyomiBackupImporterImpl): TachiyomiBackupImporter

    @Binds
    abstract fun bindTrackManager(impl: TrackManagerImpl): TrackManager

    @Binds
    abstract fun bindAppUpdateChecker(impl: AppUpdateCheckerImpl): AppUpdateChecker

    @Binds
    abstract fun bindCoverRefreshScheduler(impl: CoverRefreshSchedulerImpl): CoverRefreshScheduler

    @Binds
    abstract fun bindDownloadFolderMigrationScheduler(
        impl: DownloadFolderMigrationSchedulerImpl
    ): DownloadFolderMigrationScheduler

    @Binds
    abstract fun bindTrackerSyncScheduler(impl: TrackerSyncSchedulerImpl): TrackerSyncScheduler

    @Binds
    abstract fun bindExtensionUpdateScheduler(impl: ExtensionUpdateSchedulerImpl): ExtensionUpdateScheduler

    @Binds
    abstract fun bindDynamicCategoryRepository(impl: DynamicCategoryRepositoryImpl): DynamicCategoryRepository

    @Binds
    abstract fun bindRecommendationRepository(impl: RecommendationRepositoryImpl): RecommendationRepository

    @Binds
    abstract fun bindDataUsageRepository(impl: DataUsageRepositoryImpl): DataUsageRepository

    @Binds
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    abstract fun bindSyncScheduler(impl: SyncSchedulerImpl): SyncScheduler

    @Binds
    abstract fun bindUpdateRunSummaryRepository(impl: UpdateRunSummaryRepositoryImpl): UpdateRunSummaryRepository

    @Binds
    abstract fun bindUpdateErrorRepository(impl: UpdateErrorRepositoryImpl): UpdateErrorRepository

    @Binds
    abstract fun bindEhFavoritesRepository(impl: EhFavoritesRepositoryImpl): EhFavoritesRepository

    @Binds
    abstract fun bindMangaMetadataRepository(impl: AniListMetadataRepository): MangaMetadataRepository

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
