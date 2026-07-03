@file:Suppress("MaxLineLength")
package app.otakureader.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manga ADD COLUMN initialized INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manga ADD COLUMN viewer INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE manga ADD COLUMN chapter_flags INTEGER DEFAULT NULL")
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manga ADD COLUMN categories TEXT DEFAULT NULL")
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS manga_categories (_id INTEGER PRIMARY KEY AUTOINCREMENT, manga_id INTEGER NOT NULL, category_id INTEGER NOT NULL, FOREIGN KEY (category_id) REFERENCES categories (_id) ON DELETE CASCADE)")
    }
}

internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manga ADD COLUMN date_added INTEGER DEFAULT 0")
    }
}

internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manga ADD COLUMN update_strategy INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // reading_history
        db.execSQL("CREATE TABLE IF NOT EXISTS `reading_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chapter_id` INTEGER NOT NULL, `read_at` INTEGER NOT NULL, `read_duration_ms` INTEGER NOT NULL, FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_history_chapter_id` ON `reading_history` (`chapter_id`)")
        // opds_servers
        db.execSQL("CREATE TABLE IF NOT EXISTS `opds_servers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_opds_servers_url` ON `opds_servers` (`url`)")
        // feed_items
        db.execSQL("CREATE TABLE IF NOT EXISTS `feed_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mangaId` INTEGER NOT NULL, `mangaTitle` TEXT NOT NULL, `mangaThumbnailUrl` TEXT, `chapterId` INTEGER NOT NULL, `chapterName` TEXT NOT NULL, `chapterNumber` REAL NOT NULL, `sourceId` INTEGER NOT NULL, `sourceName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_items_sourceId` ON `feed_items` (`sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_items_timestamp` ON `feed_items` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_items_mangaId` ON `feed_items` (`mangaId`)")
        // feed_sources
        db.execSQL("CREATE TABLE IF NOT EXISTS `feed_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `sourceName` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `itemCount` INTEGER NOT NULL, `order` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_feed_sources_sourceId` ON `feed_sources` (`sourceId`)")
        // feed_saved_searches
        db.execSQL("CREATE TABLE IF NOT EXISTS `feed_saved_searches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `sourceName` TEXT NOT NULL, `query` TEXT NOT NULL, `filtersJson` TEXT, `order` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_saved_searches_sourceId` ON `feed_saved_searches` (`sourceId`)")
        // tracker_sync_state
        db.execSQL("CREATE TABLE IF NOT EXISTS `tracker_sync_state` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mangaId` INTEGER NOT NULL, `trackerId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, `localLastChapterRead` REAL NOT NULL, `localTotalChapters` INTEGER NOT NULL, `localStatus` INTEGER NOT NULL, `localLastModified` INTEGER NOT NULL, `remoteLastChapterRead` REAL NOT NULL, `remoteTotalChapters` INTEGER NOT NULL, `remoteStatus` INTEGER NOT NULL, `remoteLastModified` INTEGER, `syncStatus` INTEGER NOT NULL, `lastSyncAttempt` INTEGER, `lastSuccessfulSync` INTEGER, `syncError` TEXT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracker_sync_state_mangaId_trackerId` ON `tracker_sync_state` (`mangaId`, `trackerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracker_sync_state_syncStatus` ON `tracker_sync_state` (`syncStatus`)")
        // sync_configuration
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_configuration` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackerId` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `syncDirection` INTEGER NOT NULL, `conflictResolution` INTEGER NOT NULL, `autoSyncInterval` INTEGER NOT NULL, `syncOnChapterRead` INTEGER NOT NULL, `syncOnMarkComplete` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_configuration_trackerId` ON `sync_configuration` (`trackerId`)")
    }
}

internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Columns version/is_syncing were planned for sync but never added to the entity.
        // Migration intentionally left as no-op to preserve version chain continuity.
    }
}

internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // last_modified_at was planned for sync tracking but never added to the entity.
        // Migration intentionally left as no-op to preserve version chain continuity.
    }
}

internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // `favorite` already existed since v9 — duplicate ALTER TABLE removed.
        // `tracks` was a legacy Tachiyomi compatibility table; it is not a Room entity
        // and has no DAO, so it is omitted here to keep the schema consistent.
    }
}

internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // tracks/manga_sync were legacy Tachiyomi compatibility tables that are not Room entities
        // and have no DAOs; their creation and renaming are omitted to keep the schema consistent.
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `contentRating` INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `categorization_results`")
        db.execSQL("DROP TABLE IF EXISTS `smart_search_cache`")
        db.execSQL("DROP TABLE IF EXISTS `recommendations`")
        db.execSQL("DROP TABLE IF EXISTS `reading_patterns`")
        db.execSQL("DROP TABLE IF EXISTS `recommendation_refreshes`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_mangaId_dateFetch` ON `chapters` (`mangaId`, `dateFetch`)")
    }
}

internal val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_streaks` (
                `date` TEXT PRIMARY KEY NOT NULL,
                `chapter_count` INTEGER NOT NULL DEFAULT 0,
                `read_duration_ms` INTEGER NOT NULL DEFAULT 0,
                `last_read_at` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}

internal val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `userCompleted` INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `userDropped` INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `page_bookmarks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `manga_id` INTEGER NOT NULL,
                `chapter_id` INTEGER NOT NULL,
                `page_index` INTEGER NOT NULL,
                `note` TEXT,
                `created_at` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`manga_id`) REFERENCES `manga`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_bookmarks_chapter_id` ON `page_bookmarks` (`chapter_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_bookmarks_manga_id` ON `page_bookmarks` (`manga_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_bookmarks_manga_id_created_at` ON `page_bookmarks` (`manga_id`, `created_at`)")
    }
}

internal val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN userNotes TEXT")
    }
}

internal val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_lists` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `color` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_list_items` (
                `listId` INTEGER NOT NULL,
                `mangaId` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `addedAt` INTEGER NOT NULL,
                `note` TEXT,
                PRIMARY KEY(`listId`, `mangaId`),
                FOREIGN KEY(`listId`) REFERENCES `reading_lists`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`mangaId`) REFERENCES `manga`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_list_items_listId` ON `reading_list_items`(`listId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_list_items_mangaId` ON `reading_list_items`(`mangaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_list_items_listId_addedAt` ON `reading_list_items` (`listId`, `addedAt`)")
    }
}

internal val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `download_queue` (
                `chapter_id` INTEGER PRIMARY KEY NOT NULL,
                `manga_id` INTEGER NOT NULL,
                `manga_title` TEXT NOT NULL,
                `chapter_title` TEXT NOT NULL,
                `source_name` TEXT NOT NULL,
                `page_urls_json` TEXT NOT NULL,
                `priority` INTEGER NOT NULL DEFAULT 1,
                `status` TEXT NOT NULL DEFAULT 'QUEUED',
                `added_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

internal val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_mangaId_read_sourceOrder` ON `chapters` (`mangaId`, `read`, `sourceOrder`)")
    }
}

internal val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN update_frequency INTEGER NOT NULL DEFAULT 1")
    }
}

internal val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `track_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `manga_id` INTEGER NOT NULL,
                `tracker_id` INTEGER NOT NULL,
                `remote_id` INTEGER NOT NULL,
                `remote_url` TEXT NOT NULL DEFAULT '',
                `title` TEXT NOT NULL,
                `status` INTEGER NOT NULL,
                `last_chapter_read` REAL NOT NULL,
                `total_chapters` INTEGER NOT NULL,
                `score` REAL NOT NULL,
                `start_date` INTEGER NOT NULL,
                `finish_date` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_entries_manga_id` ON `track_entries` (`manga_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_entries_tracker_id` ON `track_entries` (`tracker_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_track_entries_manga_id_tracker_id` ON `track_entries` (`manga_id`, `tracker_id`)")
    }
}


internal val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // chapter_id is already indexed as PK; manga_id alone is covered by the composite prefix.
        db.execSQL("CREATE INDEX IF NOT EXISTS index_download_queue_manga_id_status ON download_queue(manga_id, status)")
    }
}


internal val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN lock_type TEXT DEFAULT NULL")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `dynamic_category_rules` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `category_id` INTEGER NOT NULL,
                `rule_type` TEXT NOT NULL,
                `rule_params_json` TEXT NOT NULL,
                FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dynamic_category_rules_category_id` ON `dynamic_category_rules` (`category_id`)")
    }
}

internal val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `manga_feature_cache` (
                `mangaId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `thumbnailUrl` TEXT,
                `sourceId` INTEGER NOT NULL,
                `genresJson` TEXT NOT NULL,
                `score` REAL NOT NULL,
                `lastComputed` INTEGER NOT NULL,
                PRIMARY KEY(`mangaId`)
            )
        """.trimIndent())
    }
}

internal val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS achievements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                definitionKey TEXT NOT NULL,
                unlockedAt INTEGER NOT NULL DEFAULT 0,
                progress INTEGER NOT NULL DEFAULT 0,
                target INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_achievements_definitionKey ON achievements(definitionKey)")
    }
}

/** Per-manga cover-derived theme override (#947). Nullable: null = inherit global pref. */
internal val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `mangaThemeOverride` INTEGER DEFAULT NULL")
    }
}

/** Reader progress sync outbox queue (#958). */
internal val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_queue (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            chapterId INTEGER NOT NULL,
            mangaId INTEGER NOT NULL,
            payload TEXT NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            createdAt INTEGER NOT NULL,
            lastError TEXT
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_createdAt ON sync_queue(createdAt)")
    }
}

/** Data usage dashboard + download queue bytes tracking (#956). */
internal val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS data_usage (
                date TEXT NOT NULL,
                category TEXT NOT NULL,
                network TEXT NOT NULL,
                bytes INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(date, category, network)
            )
        """.trimIndent())
        db.execSQL("ALTER TABLE download_queue ADD COLUMN bytesDownloaded INTEGER NOT NULL DEFAULT 0")
    }
}

/** User-info overrides for edit-manga-info feature (#998). */
internal val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `userTitle` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `userDescription` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `userAuthor` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `userArtist` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `userThumbnailUrl` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `userGenre` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `manga` ADD COLUMN `userStatus` INTEGER DEFAULT NULL")
    }
}

/**
 * v33 → v34: FTS4 full-text index on manga title/author/artist for instant library search.
 *
 * A content FTS4 virtual table (`manga_fts`) is created backed by the `manga` table.
 * Three triggers keep the index in sync with inserts, updates, and deletes.
 * Existing rows are bulk-inserted into the FTS index after the triggers are created.
 */
internal val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `manga_fts` " +
                "USING fts4(content=`manga`, tokenize=unicode61, `title`, `author`, `artist`)"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS manga_fts_insert AFTER INSERT ON manga BEGIN " +
                "INSERT INTO manga_fts(rowid, title, author, artist) " +
                "VALUES (new.rowid, new.title, new.author, new.artist); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS manga_fts_delete AFTER DELETE ON manga BEGIN " +
                "INSERT INTO manga_fts(manga_fts, rowid, title, author, artist) " +
                "VALUES ('delete', old.rowid, old.title, old.author, old.artist); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS manga_fts_update AFTER UPDATE ON manga BEGIN " +
                "INSERT INTO manga_fts(manga_fts, rowid, title, author, artist) " +
                "VALUES ('delete', old.rowid, old.title, old.author, old.artist); " +
                "INSERT INTO manga_fts(rowid, title, author, artist) " +
                "VALUES (new.rowid, new.title, new.author, new.artist); END"
        )
        db.execSQL(
            "INSERT INTO manga_fts(rowid, title, author, artist) " +
                "SELECT rowid, title, author, artist FROM manga"
        )
    }
}

/** v34 → v35: Library update run history diagnostics (#1041). */
internal val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS update_run_summary (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                checkedCount INTEGER NOT NULL,
                newChaptersCount INTEGER NOT NULL,
                skippedCount INTEGER NOT NULL,
                failedCount INTEGER NOT NULL,
                durationMs INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

/** v35 → v36: Manga alternative-source linking table (#1053). */
internal val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS manga_alternative_source (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                manga_id INTEGER NOT NULL,
                alt_manga_id INTEGER NOT NULL,
                FOREIGN KEY (manga_id) REFERENCES manga(id) ON DELETE CASCADE,
                FOREIGN KEY (alt_manga_id) REFERENCES manga(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_alternative_source_manga_id ON manga_alternative_source(manga_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_alternative_source_alt_manga_id ON manga_alternative_source(alt_manga_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_manga_alternative_source_manga_id_alt_manga_id ON manga_alternative_source(manga_id, alt_manga_id)")
    }
}

/** v36 → v37: Local reader comments table (chapter + book scoped). */
internal val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reader_comments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                manga_id INTEGER NOT NULL,
                chapter_id INTEGER,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (manga_id) REFERENCES manga(id) ON DELETE CASCADE,
                FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reader_comments_manga_id ON reader_comments(manga_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reader_comments_chapter_id ON reader_comments(chapter_id)")
    }
}

/** Drop the chapter-level bookmark column (replaced by the page_bookmarks table). */
internal val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Disable FK constraints for the duration of this table reconstruction.
        // reading_history and page_bookmarks reference chapters(id); dropping the old table
        // would trigger SQLITE_CONSTRAINT_FOREIGNKEY if FK enforcement is active.
        // FK constraints cannot be toggled inside an explicit transaction in strict SQLite, but
        // Android's SQLite implementation allows it and the pattern is widely used in Room migrations.
        db.execSQL("PRAGMA foreign_keys = OFF")

        // SQLite cannot DROP COLUMN directly, so we recreate the table without the bookmark column.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chapters_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                mangaId INTEGER NOT NULL,
                url TEXT NOT NULL,
                name TEXT NOT NULL,
                scanlator TEXT,
                read INTEGER NOT NULL DEFAULT 0,
                lastPageRead INTEGER NOT NULL DEFAULT 0,
                chapterNumber REAL NOT NULL DEFAULT -1,
                sourceOrder INTEGER NOT NULL DEFAULT 0,
                dateFetch INTEGER NOT NULL DEFAULT 0,
                dateUpload INTEGER NOT NULL DEFAULT 0,
                lastModified INTEGER NOT NULL DEFAULT 0,
                userNotes TEXT,
                FOREIGN KEY (mangaId) REFERENCES manga(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO chapters_new
            SELECT id, mangaId, url, name, scanlator, read, lastPageRead, chapterNumber,
                   sourceOrder, dateFetch, dateUpload, lastModified, userNotes
            FROM chapters
        """.trimIndent())
        db.execSQL("DROP TABLE chapters")
        db.execSQL("ALTER TABLE chapters_new RENAME TO chapters")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapters_mangaId ON chapters(mangaId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chapters_mangaId_url ON chapters(mangaId, url)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapters_read ON chapters(read)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapters_dateFetch ON chapters(dateFetch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapters_mangaId_dateFetch ON chapters(mangaId, dateFetch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapters_mangaId_read_sourceOrder ON chapters(mangaId, read, sourceOrder)")

        db.execSQL("PRAGMA foreign_keys = ON")
    }
}

/** Add bookmark_collections table and collection_id FK on page_bookmarks. */
internal val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bookmark_collections (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("ALTER TABLE page_bookmarks ADD COLUMN collection_id INTEGER REFERENCES bookmark_collections(id) ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_page_bookmarks_collection_id ON page_bookmarks(collection_id)")
    }
}

/** Add update_errors table for the Update Errors screen's current-unresolved-failures list. */
internal val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS update_errors (
                mangaId INTEGER PRIMARY KEY NOT NULL,
                errorMessage TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (mangaId) REFERENCES manga(id) ON DELETE CASCADE
            )
        """.trimIndent())
    }
}

/** All migrations in order, for use in [Room.databaseBuilder] and migration tests. */
internal val ALL_MIGRATIONS = arrayOf(
    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
    MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
    MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26,
    MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31,
    MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35,
    MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40,
)
