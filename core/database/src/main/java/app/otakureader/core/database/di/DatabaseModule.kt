package app.otakureader.core.database.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import app.otakureader.core.database.BuildConfig
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.dao.AchievementDao
import app.otakureader.core.database.dao.BookmarkCollectionDao
import app.otakureader.core.database.dao.DataUsageDao
import app.otakureader.core.database.dao.DownloadQueueDao
import app.otakureader.core.database.dao.MangaAlternativeSourceDao
import app.otakureader.core.database.dao.MangaMetadataDao
import app.otakureader.core.database.dao.SyncQueueDao
import app.otakureader.core.database.dao.TrackEntryDao
import app.otakureader.core.database.dao.UpdateErrorDao
import app.otakureader.core.database.dao.UpdateRunSummaryDao
import app.otakureader.core.database.migrations.ALL_MIGRATIONS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * One `@Provides` per DAO, so this grows by exactly one function every time a table is added.
 *
 * [TooManyFunctions] measures a shape it was not written for here: the count is a list length, not
 * complexity — every function is the same three-token body and none of them can interact. Splitting
 * the object to satisfy the threshold would scatter a flat registry across arbitrary boundaries and
 * make "where is this DAO provided" a search instead of a scroll.
 */
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): OtakuReaderDatabase {
        val builder = Room.databaseBuilder(
            context,
            OtakuReaderDatabase::class.java,
            OtakuReaderDatabase.DATABASE_NAME
        )
            .addMigrations(*ALL_MIGRATIONS)
        // Only allow destructive migration in debug builds to avoid silently wiping
        // user data (including notes) in production if a migration is missing.
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration(dropAllTables = true)
            builder.setQueryCallback(
                { sqlQuery, _ -> Log.d("RoomQuery", sqlQuery) },
                Executors.newSingleThreadExecutor()
            )
        }
        return builder.build()
    }

    @Provides
    fun provideMangaDao(database: OtakuReaderDatabase) = database.mangaDao()

    @Provides
    fun provideChapterDao(database: OtakuReaderDatabase) = database.chapterDao()

    @Provides
    fun provideCategoryDao(database: OtakuReaderDatabase) = database.categoryDao()

    @Provides
    fun provideMangaCategoryDao(database: OtakuReaderDatabase) = database.mangaCategoryDao()

    @Provides
    fun provideReadingHistoryDao(database: OtakuReaderDatabase) = database.readingHistoryDao()

    @Provides
    fun provideOpdsServerDao(database: OtakuReaderDatabase) = database.opdsServerDao()

    @Provides
    fun provideFeedDao(database: OtakuReaderDatabase) = database.feedDao()

    @Provides
    fun provideTrackerSyncDao(database: OtakuReaderDatabase) = database.trackerSyncDao()

    @Provides
    fun provideReadingStreakDao(database: OtakuReaderDatabase) = database.readingStreakDao()

    @Provides
    fun provideReadingListDao(database: OtakuReaderDatabase) = database.readingListDao()

    @Provides
    fun providePageBookmarkDao(database: OtakuReaderDatabase) = database.pageBookmarkDao()

    @Provides
    fun provideBookmarkCollectionDao(database: OtakuReaderDatabase): BookmarkCollectionDao = database.bookmarkCollectionDao()

    @Provides
    fun provideDownloadQueueDao(database: OtakuReaderDatabase): DownloadQueueDao = database.downloadQueueDao()

    @Provides
    fun provideTrackEntryDao(database: OtakuReaderDatabase): TrackEntryDao = database.trackEntryDao()

    @Provides
    fun provideRecommendationDao(database: OtakuReaderDatabase) = database.recommendationDao()

    @Provides
    fun provideDynamicCategoryRuleDao(database: OtakuReaderDatabase) = database.dynamicCategoryRuleDao()

    @Provides
    fun provideAchievementDao(database: OtakuReaderDatabase): AchievementDao = database.achievementDao()

    @Provides
    fun provideDataUsageDao(database: OtakuReaderDatabase): DataUsageDao = database.dataUsageDao()

    @Provides
    fun provideSyncQueueDao(database: OtakuReaderDatabase): SyncQueueDao = database.syncQueueDao()

    @Provides
    fun provideUpdateRunSummaryDao(database: OtakuReaderDatabase): UpdateRunSummaryDao = database.updateRunSummaryDao()

    @Provides
    fun provideMangaAlternativeSourceDao(
        database: OtakuReaderDatabase,
    ): MangaAlternativeSourceDao = database.mangaAlternativeSourceDao()

    @Provides
    fun provideReaderCommentDao(database: OtakuReaderDatabase) = database.readerCommentDao()

    @Provides
    fun provideUpdateErrorDao(database: OtakuReaderDatabase): UpdateErrorDao = database.updateErrorDao()

    @Provides
    fun provideMangaMetadataDao(database: OtakuReaderDatabase): MangaMetadataDao = database.mangaMetadataDao()
}
