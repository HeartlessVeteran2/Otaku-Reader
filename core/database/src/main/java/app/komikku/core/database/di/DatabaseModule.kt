package app.komikku.core.database.di

import android.content.Context
import androidx.room.Room
import app.komikku.core.database.KomikkuDatabase
import app.komikku.core.database.dao.ChapterDao
import app.komikku.core.database.dao.MangaDao
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
            "komikku.db",
        ).build()

    @Provides
    fun provideMangaDao(database: KomikkuDatabase): MangaDao = database.mangaDao()

    @Provides
    fun provideChapterDao(database: KomikkuDatabase): ChapterDao = database.chapterDao()
}
