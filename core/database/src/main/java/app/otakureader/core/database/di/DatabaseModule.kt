package app.otakureader.core.database.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import app.otakureader.core.database.BuildConfig
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.migrations.ALL_MIGRATIONS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Singleton

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
}
