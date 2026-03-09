package app.otakureader.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.otakureader.core.database.BuildConfig
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

    /**
     * Adds the reading_history table introduced in database version 3.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reading_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `chapter_id` INTEGER NOT NULL,
                    `read_at` INTEGER NOT NULL DEFAULT 0,
                    `read_duration_ms` INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_history_chapter_id` " +
                    "ON `reading_history` (`chapter_id`)"
            )
        }
    }

    /**
     * Adds the autoDownload column to the manga table in database version 4.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `autoDownload` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Adds the notes column to the manga table in database version 5.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `notes` TEXT")
        }
    }

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
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        // Only allow destructive migration in debug builds to avoid silently wiping
        // user data (including notes) in production if a migration is missing.
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration(dropAllTables = true)
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
}
