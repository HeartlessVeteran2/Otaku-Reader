package app.komikku.core.database.di

import android.content.Context
import androidx.room.Room
import app.komikku.core.database.KomikkuDatabase
import app.komikku.core.database.dao.CategoryDao
import app.komikku.core.database.dao.ChapterDao
import app.komikku.core.database.dao.MangaCategoryDao
import app.komikku.core.database.dao.MangaDao
import app.komikku.core.database.dao.ReadingHistoryDao
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
    fun provideDatabase(@ApplicationContext context: Context): KomikkuDatabase =
        Room.databaseBuilder(
            context,
            KomikkuDatabase::class.java,
            KomikkuDatabase.DATABASE_NAME
        ).build()

    @Provides
    fun provideMangaDao(db: KomikkuDatabase): MangaDao = db.mangaDao()

    @Provides
    fun provideChapterDao(db: KomikkuDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun provideCategoryDao(db: KomikkuDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideMangaCategoryDao(db: KomikkuDatabase): MangaCategoryDao = db.mangaCategoryDao()

    @Provides
    fun provideReadingHistoryDao(db: KomikkuDatabase): ReadingHistoryDao = db.readingHistoryDao()
}
