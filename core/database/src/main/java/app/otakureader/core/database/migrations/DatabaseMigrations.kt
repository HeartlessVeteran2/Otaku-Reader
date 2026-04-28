package app.otakureader.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_2_3 = object : Migration(2, 3) {
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

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `autoDownload` INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `notes` TEXT")
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `notifyNewChapters` INTEGER NOT NULL DEFAULT 1")
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chapters_dateFetch` ON `chapters` (`dateFetch`)"
        )
    }
}

internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerDirection` INTEGER")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerMode` INTEGER")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerColorFilter` INTEGER")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerCustomTintColor` INTEGER")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `preloadPagesBefore` INTEGER")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `preloadPagesAfter` INTEGER")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `readerBackgroundColor` INTEGER")
    }
}

internal val MIGRATION_8_9 = object : Migration(8, 9) {
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

internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
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

internal val MIGRATION_10_11 = object : Migration(10, 11) {
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

internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `smart_search_cache` (
                `queryHash` TEXT PRIMARY KEY NOT NULL,
                `originalQuery` TEXT NOT NULL,
                `parsedQueryJson` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recommendations` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `mangaId` INTEGER,
                `title` TEXT NOT NULL,
                `author` TEXT,
                `thumbnailUrl` TEXT,
                `description` TEXT,
                `genres` TEXT NOT NULL DEFAULT '',
                `sourceId` TEXT NOT NULL,
                `sourceUrl` TEXT NOT NULL,
                `reasonExplanation` TEXT NOT NULL,
                `confidenceScore` REAL NOT NULL DEFAULT 0.0,
                `basedOnMangaIds` TEXT NOT NULL DEFAULT '',
                `basedOnGenres` TEXT NOT NULL DEFAULT '',
                `recommendationType` TEXT NOT NULL DEFAULT 'SIMILAR',
                `generatedAt` INTEGER NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                `viewed` INTEGER NOT NULL DEFAULT 0,
                `actioned` INTEGER NOT NULL DEFAULT 0,
                `dismissed` INTEGER NOT NULL DEFAULT 0,
                `actionedMangaId` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendations_generatedAt` ON `recommendations` (`generatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendations_recommendationType` ON `recommendations` (`recommendationType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendations_viewed` ON `recommendations` (`viewed`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendations_dismissed` ON `recommendations` (`dismissed`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_patterns` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `favoriteGenres` TEXT NOT NULL DEFAULT '',
                `favoriteAuthors` TEXT NOT NULL DEFAULT '',
                `preferredStatus` TEXT NOT NULL DEFAULT '',
                `averageReadingTimeMs` INTEGER NOT NULL DEFAULT 0,
                `preferredMinChapters` INTEGER,
                `preferredMaxChapters` INTEGER,
                `commonThemes` TEXT NOT NULL DEFAULT '',
                `readingVelocity` TEXT NOT NULL DEFAULT 'MODERATE',
                `favoriteTropes` TEXT NOT NULL DEFAULT '',
                `generatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_patterns_generatedAt` ON `reading_patterns` (`generatedAt`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recommendation_refreshes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `success` INTEGER NOT NULL DEFAULT 1,
                `errorMessage` TEXT,
                `recommendationsCount` INTEGER NOT NULL DEFAULT 0,
                `patternId` TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recommendation_refreshes_timestamp` ON `recommendation_refreshes` (`timestamp`)")
    }
}

internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chapters_mangaId_dateFetch` ON `chapters` (`mangaId`, `dateFetch`)"
        )
    }
}

internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `contentRating` INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop AI-specific tables extracted to the sibling Otaku-Reader-AI repo.
        db.execSQL("DROP TABLE IF EXISTS `categorization_results`")
        db.execSQL("DROP TABLE IF EXISTS `smart_search_cache`")
        db.execSQL("DROP TABLE IF EXISTS `recommendations`")
        db.execSQL("DROP TABLE IF EXISTS `reading_patterns`")
        db.execSQL("DROP TABLE IF EXISTS `recommendation_refreshes`")
    }
}

/** All migrations in order, for use in [Room.databaseBuilder] and migration tests. */
internal val ALL_MIGRATIONS = arrayOf(
    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
    MIGRATION_14_15
)
