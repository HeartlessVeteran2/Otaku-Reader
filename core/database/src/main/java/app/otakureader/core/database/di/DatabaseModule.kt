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

    /**
     * Adds the notifyNewChapters column to the manga table in database version 6.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `notifyNewChapters` INTEGER NOT NULL DEFAULT 1")
        }
    }

    /**
     * Adds an index on chapters(dateFetch) in database version 7.
     * This speeds up the badge-counter query that counts chapters with dateFetch > timestamp.
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chapters_dateFetch` ON `chapters` (`dateFetch`)"
            )
        }
    }

    /**
     * Adds per-manga reader settings, page preloading configuration, and reader background color
     * in database version 8. Issues #260, #264, and AMOLED theme support.
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Per-manga reader settings (#260)
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerDirection` INTEGER")
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerMode` INTEGER")
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerColorFilter` INTEGER")
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerCustomTintColor` INTEGER")
            // Page preloading settings (#264)
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `preloadPagesBefore` INTEGER")
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `preloadPagesAfter` INTEGER")
            // Reader background color (AMOLED theme support)
            db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerBackgroundColor` INTEGER")
        }
    }

    /**
     * Adds the opds_servers table for OPDS server management in database version 9.
     * Credentials are stored separately in EncryptedSharedPreferences.
     */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `opds_servers` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `url` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_opds_servers_url` ON `opds_servers` (`url`)"
            )
        }
    }

    /**
     * Adds Feed feature tables and Tracker Sync tables in database version 10.
     */
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Feed feature tables
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `feed_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `mangaId` INTEGER NOT NULL,
                    `mangaTitle` TEXT NOT NULL,
                    `mangaThumbnailUrl` TEXT,
                    `chapterId` INTEGER NOT NULL,
                    `chapterName` TEXT NOT NULL,
                    `chapterNumber` REAL NOT NULL,
                    `sourceId` INTEGER NOT NULL,
                    `sourceName` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `isRead` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_items_sourceId` ON `feed_items` (`sourceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_items_timestamp` ON `feed_items` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_items_mangaId` ON `feed_items` (`mangaId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `feed_sources` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sourceId` INTEGER NOT NULL,
                    `sourceName` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `itemCount` INTEGER NOT NULL DEFAULT 20,
                    `order` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_feed_sources_sourceId` ON `feed_sources` (`sourceId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `feed_saved_searches` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sourceId` INTEGER NOT NULL,
                    `sourceName` TEXT NOT NULL,
                    `query` TEXT NOT NULL,
                    `filtersJson` TEXT,
                    `order` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_saved_searches_sourceId` ON `feed_saved_searches` (`sourceId`)")

            // Tracker Sync tables
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tracker_sync_state` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `mangaId` INTEGER NOT NULL,
                    `trackerId` INTEGER NOT NULL,
                    `remoteId` TEXT NOT NULL,
                    `localLastChapterRead` REAL NOT NULL,
                    `localTotalChapters` INTEGER NOT NULL,
                    `localStatus` INTEGER NOT NULL,
                    `localLastModified` INTEGER NOT NULL,
                    `remoteLastChapterRead` REAL NOT NULL,
                    `remoteTotalChapters` INTEGER NOT NULL,
                    `remoteStatus` INTEGER NOT NULL,
                    `remoteLastModified` INTEGER,
                    `syncStatus` INTEGER NOT NULL,
                    `lastSyncAttempt` INTEGER,
                    `lastSuccessfulSync` INTEGER,
                    `syncError` TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracker_sync_state_mangaId_trackerId` ON `tracker_sync_state` (`mangaId`, `trackerId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracker_sync_state_syncStatus` ON `tracker_sync_state` (`syncStatus`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_configuration` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackerId` INTEGER NOT NULL UNIQUE,
                    `enabled` INTEGER NOT NULL DEFAULT 1,
                    `syncDirection` INTEGER NOT NULL,
                    `conflictResolution` INTEGER NOT NULL,
                    `autoSyncInterval` INTEGER NOT NULL DEFAULT 300000,
                    `syncOnChapterRead` INTEGER NOT NULL DEFAULT 1,
                    `syncOnMarkComplete` INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Adds categorization_results table for AI auto-categorization feature in database version 11.
     */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `categorization_results` (
                    `mangaId` INTEGER PRIMARY KEY NOT NULL,
                    `suggestionsJson` TEXT NOT NULL,
                    `appliedCategoriesJson` TEXT NOT NULL,
                    `wasAutoApplied` INTEGER NOT NULL,
                    `wasReviewed` INTEGER NOT NULL DEFAULT 0,
                    `timestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categorization_results_timestamp` ON `categorization_results` (`timestamp`)")
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
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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

    @Provides
    fun provideOpdsServerDao(database: OtakuReaderDatabase) = database.opdsServerDao()

    @Provides
    fun provideFeedDao(database: OtakuReaderDatabase) = database.feedDao()

    @Provides
    fun provideTrackerSyncDao(database: OtakuReaderDatabase) = database.trackerSyncDao()

    @Provides
    fun provideCategorizationResultDao(database: OtakuReaderDatabase) = database.categorizationResultDao()
}
