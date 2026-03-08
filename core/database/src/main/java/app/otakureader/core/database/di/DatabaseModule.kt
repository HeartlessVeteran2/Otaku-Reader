package app.otakureader.core.database.di

import android.content.Context
import androidx.room.Room
import app.otakureader.core.database.OtakuReaderDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): OtakuReaderDatabase {
        return Room.databaseBuilder(
            context,
            OtakuReaderDatabase::class.java,
            OtakuReaderDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    fun provideMangaDao(database: OtakuReaderDatabase) = database.mangaDao()
    
    @Provides
    fun provideChapterDao(database: OtakuReaderDatabase) = database.chapterDao()
    
    @Provides
    fun provideCategoryDao(database: OtakuReaderDatabase) = database.categoryDao()

    @Provides
    fun provideReadingHistoryDao(database: OtakuReaderDatabase) = database.readingHistoryDao()
}
