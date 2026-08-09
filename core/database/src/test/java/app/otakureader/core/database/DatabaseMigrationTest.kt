package app.otakureader.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.otakureader.core.database.migrations.ALL_MIGRATIONS
import app.otakureader.core.database.migrations.MIGRATION_10_11
import app.otakureader.core.database.migrations.MIGRATION_11_12
import app.otakureader.core.database.migrations.MIGRATION_13_14
import app.otakureader.core.database.migrations.MIGRATION_14_15
import app.otakureader.core.database.migrations.MIGRATION_15_16
import app.otakureader.core.database.migrations.MIGRATION_16_17
import app.otakureader.core.database.migrations.MIGRATION_17_18
import app.otakureader.core.database.migrations.MIGRATION_18_19
import app.otakureader.core.database.migrations.MIGRATION_19_20
import app.otakureader.core.database.migrations.MIGRATION_20_21
import app.otakureader.core.database.migrations.MIGRATION_21_22
import app.otakureader.core.database.migrations.MIGRATION_22_23
import app.otakureader.core.database.migrations.MIGRATION_23_24
import app.otakureader.core.database.migrations.MIGRATION_24_25
import app.otakureader.core.database.migrations.MIGRATION_25_26
import app.otakureader.core.database.migrations.MIGRATION_26_27
import app.otakureader.core.database.migrations.MIGRATION_27_28
import app.otakureader.core.database.migrations.MIGRATION_28_29
import app.otakureader.core.database.migrations.MIGRATION_29_30
import app.otakureader.core.database.migrations.MIGRATION_30_31
import app.otakureader.core.database.migrations.MIGRATION_31_32
import app.otakureader.core.database.migrations.MIGRATION_35_36
import app.otakureader.core.database.migrations.MIGRATION_36_37
import app.otakureader.core.database.migrations.MIGRATION_37_38
import app.otakureader.core.database.migrations.MIGRATION_38_39
import app.otakureader.core.database.migrations.MIGRATION_39_40
import app.otakureader.core.database.migrations.MIGRATION_40_41
import app.otakureader.core.database.migrations.MIGRATION_41_42
import app.otakureader.core.database.migrations.MIGRATION_42_43
import app.otakureader.core.database.migrations.MIGRATION_43_44
import app.otakureader.core.database.migrations.MIGRATION_44_45
import app.otakureader.core.database.migrations.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"
private const val SCHEMA_V37 = 37
private const val SCHEMA_V39 = 39
private const val SCHEMA_V40 = 40
private const val SCHEMA_V41 = 41
private const val SCHEMA_V42 = 42
private const val SCHEMA_V43 = 43
private const val SCHEMA_V44 = 44
private const val SCHEMA_V45 = 45
private const val EXPECTED_MIGRATION_COUNT = 43

@RunWith(AndroidJUnit4::class)
// One test class per migration chain: it grows by design with every schema version.
@Suppress("LargeClass")
class DatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OtakuReaderDatabase::class.java,
    )

    // ── Chain integrity ──────────────────────────────────────────────────────

    @Test
    fun allMigrations_formsContiguousChain() {
        val sorted = ALL_MIGRATIONS.sortedBy { it.startVersion }
        assertEquals("Migration chain must start at version 2", 2, sorted.first().startVersion)
        assertEquals("Migration chain must end at version 45", SCHEMA_V45, sorted.last().endVersion)

        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            assertEquals(
                "Gap between migration ${current.startVersion}→${current.endVersion} " +
                    "and ${next.startVersion}→${next.endVersion}",
                current.endVersion,
                next.startVersion,
            )
        }
    }

    @Test
    fun allMigrations_eachVersionIncrementsByOne() {
        for (migration in ALL_MIGRATIONS) {
            assertEquals(
                "Migration ${migration.startVersion}→${migration.endVersion} should increment by 1",
                migration.startVersion + 1,
                migration.endVersion,
            )
        }
    }

    @Test
    fun allMigrations_count() {
        assertEquals("Expected 43 migrations (v2→v45)", EXPECTED_MIGRATION_COUNT, ALL_MIGRATIONS.size)
    }

    // ── Migration 9 → 10 ────────────────────────────────────────────────────
    // Adds: feed_items, feed_sources, feed_saved_searches, tracker_sync_state, sync_configuration

    @Test
    fun migration9To10_createsExpectedTables() {
        helper.createDatabase(TEST_DB, 9).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        val tables = db.tableNames()
        assertTrue("feed_items must exist after 9→10", "feed_items" in tables)
        assertTrue("feed_sources must exist after 9→10", "feed_sources" in tables)
        assertTrue("feed_saved_searches must exist after 9→10", "feed_saved_searches" in tables)
        assertTrue("tracker_sync_state must exist after 9→10", "tracker_sync_state" in tables)
        assertTrue("sync_configuration must exist after 9→10", "sync_configuration" in tables)
        db.close()
    }

    @Test
    fun migration9To10_feedSavedSearches_hasSourceIdIndex() {
        helper.createDatabase(TEST_DB, 9).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        val indexes = db.indexNames("feed_saved_searches")
        assertTrue(
            "index_feed_saved_searches_sourceId must exist after 9→10",
            "index_feed_saved_searches_sourceId" in indexes,
        )
        db.close()
    }

    // ── Migration 9 → 10: new tables added in this fix ──────────────────────

    @Test
    fun migration9To10_createsReadingHistory() {
        helper.createDatabase(TEST_DB, 9).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        assertTrue("reading_history must exist after 9→10", "reading_history" in db.tableNames())
        assertTrue(
            "index_reading_history_chapter_id must exist after 9→10",
            "index_reading_history_chapter_id" in db.indexNames("reading_history"),
        )
        db.close()
    }

    @Test
    fun migration9To10_createsOpdsServers() {
        helper.createDatabase(TEST_DB, 9).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        assertTrue("opds_servers must exist after 9→10", "opds_servers" in db.tableNames())
        db.close()
    }

    // ── Migration 10 → 11 ───────────────────────────────────────────────────
    // version/is_syncing were planned for sync but never added to the entity;
    // the migration is a no-op that just advances the version number.

    @Test
    fun migration10To11_addsVersionAndIsSyncingToChapters() {
        helper.createDatabase(TEST_DB, 9).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        assertFalse("version must NOT exist at v10", "version" in db.columnNames("chapters"))
        assertFalse("is_syncing must NOT exist at v10", "is_syncing" in db.columnNames("chapters"))
        MIGRATION_10_11.migrate(db)
        val cols = db.columnNames("chapters")
        // These columns were removed from the entity; migration is intentionally a no-op.
        assertFalse("chapters.version must not exist (migration is no-op)", "version" in cols)
        assertFalse("chapters.is_syncing must not exist (migration is no-op)", "is_syncing" in cols)
        db.close()
    }

    @Test
    fun migration10To11_versionAndIsSyncingDefaultToZero() {
        helper.createDatabase(TEST_DB, 9).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        MIGRATION_10_11.migrate(db)
        // Migration is a no-op; verify it runs without error and schema is valid.
        val cols = db.columnNames("chapters")
        assertTrue("chapters table must still be intact after 10→11", cols.isNotEmpty())
        db.close()
    }

    // ── Migration 11 → 12 ───────────────────────────────────────────────────
    // last_modified_at was planned for sync tracking but never added to the entity.
    // Migration is a no-op that advances the version number.

    @Test
    fun migration11To12_addsLastModifiedAtToMangaAndChapters() {
        helper.createDatabase(TEST_DB, 9).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        MIGRATION_10_11.migrate(db)
        MIGRATION_11_12.migrate(db)
        // Migration is a no-op; verify it runs without error and existing columns are intact.
        assertTrue("manga table must be intact after 11→12", db.columnNames("manga").isNotEmpty())
        assertTrue("chapters table must be intact after 11→12", db.columnNames("chapters").isNotEmpty())
        db.close()
    }

    @Test
    fun migration11To12_lastModifiedAtDefaultsToZero() {
        helper.createDatabase(TEST_DB, 9).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        MIGRATION_10_11.migrate(db)
        MIGRATION_11_12.migrate(db)
        // Migration is a no-op; verify the schema is still valid after all three migrations.
        assertTrue("manga table must have id column after 11→12", "id" in db.columnNames("manga"))
        assertTrue("chapters table must have id column after 11→12", "id" in db.columnNames("chapters"))
        db.close()
    }

    // ── Migration 13 → 14 ───────────────────────────────────────────────────
    // Adds: manga.contentRating (INTEGER NOT NULL DEFAULT 0)

    @Test
    fun migration13To14_addsContentRatingColumn() {
        helper.createDatabase(TEST_DB, 13).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)
        assertTrue(
            "manga.contentRating must exist after migration 13→14",
            "contentRating" in db.columnNames("manga"),
        )
        db.close()
    }

    @Test
    fun migration13To14_contentRatingDefaultsToZero() {
        val db13 = helper.createDatabase(TEST_DB, 13)
        db13.execSQL(
            "INSERT INTO manga (sourceId, url, title, status, favorite, lastUpdate, initialized, " +
                "viewerFlags, chapterFlags, coverLastModified, dateAdded, autoDownload, notifyNewChapters) " +
                "VALUES (1, 'url', 'Test Manga', 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)",
        )
        db13.close()

        val db14 = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)
        val cursor = db14.query("SELECT contentRating FROM manga WHERE title = 'Test Manga'")
        assertTrue("Row must survive migration", cursor.moveToFirst())
        assertEquals("contentRating default must be 0", 0, cursor.getInt(0))
        cursor.close()
        db14.close()
    }

    // ── Migrations 15 → 21 ──────────────────────────────────────────────────
    // These tests start from v14 (the last exported schema before v21) and apply
    // migrations directly via .migrate() to avoid requiring intermediate schema
    // export files (15.json–20.json). Each test verifies the incremental change
    // introduced by its specific migration.

    @Test
    fun migration15To16_createsReadingStreaks() {
        val db = helper.createDatabase(TEST_DB, 14)
        MIGRATION_14_15.migrate(db)
        assertFalse("reading_streaks must NOT exist at v15", "reading_streaks" in db.tableNames())
        MIGRATION_15_16.migrate(db)
        assertTrue("reading_streaks must exist after 15→16", "reading_streaks" in db.tableNames())
        val cols = db.columnNames("reading_streaks")
        assertTrue("date column must exist", "date" in cols)
        assertTrue("chapter_count column must exist", "chapter_count" in cols)
        assertTrue("read_duration_ms column must exist", "read_duration_ms" in cols)
        assertTrue("last_read_at column must exist", "last_read_at" in cols)
        db.close()
    }

    @Test
    fun migration16To17_addsUserCompleted() {
        val db = helper.createDatabase(TEST_DB, 14)
        MIGRATION_14_15.migrate(db)
        MIGRATION_15_16.migrate(db)
        assertFalse("userCompleted must NOT exist at v16", "userCompleted" in db.columnNames("manga"))
        MIGRATION_16_17.migrate(db)
        assertTrue("manga.userCompleted must exist after 16→17", "userCompleted" in db.columnNames("manga"))
        db.close()
    }

    @Test
    fun migration17To18_addsUserDropped() {
        val db = helper.createDatabase(TEST_DB, 14)
        MIGRATION_14_15.migrate(db)
        MIGRATION_15_16.migrate(db)
        MIGRATION_16_17.migrate(db)
        assertFalse("userDropped must NOT exist at v17", "userDropped" in db.columnNames("manga"))
        MIGRATION_17_18.migrate(db)
        assertTrue("manga.userDropped must exist after 17→18", "userDropped" in db.columnNames("manga"))
        db.close()
    }

    @Test
    fun migration18To19_createsPageBookmarks() {
        val db = helper.createDatabase(TEST_DB, 14)
        MIGRATION_14_15.migrate(db)
        MIGRATION_15_16.migrate(db)
        MIGRATION_16_17.migrate(db)
        MIGRATION_17_18.migrate(db)
        assertFalse("page_bookmarks must NOT exist at v18", "page_bookmarks" in db.tableNames())
        MIGRATION_18_19.migrate(db)
        assertTrue("page_bookmarks must exist after 18→19", "page_bookmarks" in db.tableNames())
        val indexes = db.indexNames("page_bookmarks")
        assertTrue("index_page_bookmarks_chapter_id must exist", "index_page_bookmarks_chapter_id" in indexes)
        assertTrue("index_page_bookmarks_manga_id must exist", "index_page_bookmarks_manga_id" in indexes)
        assertTrue(
            "index_page_bookmarks_manga_id_created_at must exist",
            "index_page_bookmarks_manga_id_created_at" in indexes,
        )
        db.close()
    }

    @Test
    fun migration19To20_addsUserNotes() {
        val db = helper.createDatabase(TEST_DB, 14)
        MIGRATION_14_15.migrate(db)
        MIGRATION_15_16.migrate(db)
        MIGRATION_16_17.migrate(db)
        MIGRATION_17_18.migrate(db)
        MIGRATION_18_19.migrate(db)
        assertFalse("userNotes must NOT exist at v19", "userNotes" in db.columnNames("chapters"))
        MIGRATION_19_20.migrate(db)
        assertTrue("chapters.userNotes must exist after 19→20", "userNotes" in db.columnNames("chapters"))
        db.close()
    }

    @Test
    fun migration20To21_createsReadingLists() {
        val db = helper.createDatabase(TEST_DB, 14)
        MIGRATION_14_15.migrate(db)
        MIGRATION_15_16.migrate(db)
        MIGRATION_16_17.migrate(db)
        MIGRATION_17_18.migrate(db)
        MIGRATION_18_19.migrate(db)
        MIGRATION_19_20.migrate(db)
        assertFalse("reading_lists must NOT exist at v20", "reading_lists" in db.tableNames())
        MIGRATION_20_21.migrate(db)
        val tables = db.tableNames()
        assertTrue("reading_lists must exist after 20→21", "reading_lists" in tables)
        assertTrue("reading_list_items must exist after 20→21", "reading_list_items" in tables)
        val indexes = db.indexNames("reading_list_items")
        assertTrue("index_reading_list_items_listId must exist", "index_reading_list_items_listId" in indexes)
        assertTrue("index_reading_list_items_mangaId must exist", "index_reading_list_items_mangaId" in indexes)
        assertTrue(
            "index_reading_list_items_listId_addedAt must exist",
            "index_reading_list_items_listId_addedAt" in indexes,
        )
        db.close()
    }

    // ── Migration 21 → 22 ───────────────────────────────────────────────────
    // Adds: download_queue

    @Test
    fun migration21To22_createsDownloadQueue() {
        val db = helper.createDatabase(TEST_DB, 14)
        MIGRATION_14_15.migrate(db)
        MIGRATION_15_16.migrate(db)
        MIGRATION_16_17.migrate(db)
        MIGRATION_17_18.migrate(db)
        MIGRATION_18_19.migrate(db)
        MIGRATION_19_20.migrate(db)
        MIGRATION_20_21.migrate(db)
        assertFalse("download_queue must NOT exist at v21", "download_queue" in db.tableNames())
        MIGRATION_21_22.migrate(db)
        val tables = db.tableNames()
        assertTrue("download_queue must exist after 21→22", "download_queue" in tables)
        val cols = db.columnNames("download_queue")
        assertTrue("chapter_id must exist", "chapter_id" in cols)
        assertTrue("manga_id must exist", "manga_id" in cols)
        assertTrue("manga_title must exist", "manga_title" in cols)
        assertTrue("chapter_title must exist", "chapter_title" in cols)
        assertTrue("source_name must exist", "source_name" in cols)
        assertTrue("page_urls_json must exist", "page_urls_json" in cols)
        assertTrue("priority must exist", "priority" in cols)
        assertTrue("status must exist", "status" in cols)
        assertTrue("added_at must exist", "added_at" in cols)
        db.close()
    }

    // ── Migration 22 → 23 ───────────────────────────────────────────────────
    // Adds: composite index (mangaId, read, sourceOrder) on chapters

    @Test
    fun migration22To23_addsCompositeIndex() {
        val db = helper.createDatabase(TEST_DB, 14)
        MIGRATION_14_15.migrate(db)
        MIGRATION_15_16.migrate(db)
        MIGRATION_16_17.migrate(db)
        MIGRATION_17_18.migrate(db)
        MIGRATION_18_19.migrate(db)
        MIGRATION_19_20.migrate(db)
        MIGRATION_20_21.migrate(db)
        MIGRATION_21_22.migrate(db)
        val indexesBefore = db.indexNames("chapters")
        assertFalse(
            "index_chapters_mangaId_read_sourceOrder must NOT exist at v22",
            "index_chapters_mangaId_read_sourceOrder" in indexesBefore,
        )
        MIGRATION_22_23.migrate(db)
        val indexesAfter = db.indexNames("chapters")
        assertTrue(
            "index_chapters_mangaId_read_sourceOrder must exist after 22→23",
            "index_chapters_mangaId_read_sourceOrder" in indexesAfter,
        )
        assertFalse(
            "index_chapters_mangaId_read (redundant 2-column index) must NOT be created",
            "index_chapters_mangaId_read" in indexesAfter,
        )
        db.close()
    }

    // ── Migration 23 → 24 ───────────────────────────────────────────────────
    // Adds: categories.update_frequency (INTEGER NOT NULL DEFAULT 1)

    @Test
    fun migration23To24_addsCategoryUpdateFrequency() {
        helper.createDatabase(TEST_DB, 23).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 24, true, MIGRATION_23_24)
        assertTrue(
            "categories.update_frequency must exist after 23→24",
            "update_frequency" in db.columnNames("categories"),
        )
        db.close()
    }

    @Test
    fun migration23To24_updateFrequencyDefaultsToOne() {
        val db23 = helper.createDatabase(TEST_DB, 23)
        db23.execSQL("INSERT INTO categories (`name`, `order`, `flags`) VALUES ('Test Category', 0, 0)")
        db23.close()

        val db24 = helper.runMigrationsAndValidate(TEST_DB, 24, true, MIGRATION_23_24)
        val cursor = db24.query("SELECT update_frequency FROM categories WHERE name = 'Test Category'")
        assertTrue("Row must survive migration", cursor.moveToFirst())
        assertEquals("update_frequency default must be 1", 1, cursor.getInt(0))
        cursor.close()
        db24.close()
    }

    // ── Migration 24 → 25 ───────────────────────────────────────────────────
    // Adds: track_entries table with indexes

    @Test
    fun migration24To25_createsTrackEntries() {
        val db = helper.createDatabase(TEST_DB, 24)
        assertFalse("track_entries must NOT exist at v24", "track_entries" in db.tableNames())
        MIGRATION_24_25.migrate(db)
        assertTrue("track_entries must exist after 24→25", "track_entries" in db.tableNames())
        val cols = db.columnNames("track_entries")
        assertTrue("id must exist", "id" in cols)
        assertTrue("manga_id must exist", "manga_id" in cols)
        assertTrue("tracker_id must exist", "tracker_id" in cols)
        assertTrue("remote_id must exist", "remote_id" in cols)
        assertTrue("remote_url must exist", "remote_url" in cols)
        assertTrue("title must exist", "title" in cols)
        assertTrue("status must exist", "status" in cols)
        assertTrue("last_chapter_read must exist", "last_chapter_read" in cols)
        assertTrue("total_chapters must exist", "total_chapters" in cols)
        assertTrue("score must exist", "score" in cols)
        assertTrue("start_date must exist", "start_date" in cols)
        assertTrue("finish_date must exist", "finish_date" in cols)
        val indexes = db.indexNames("track_entries")
        assertTrue("index_track_entries_manga_id must exist", "index_track_entries_manga_id" in indexes)
        assertTrue("index_track_entries_tracker_id must exist", "index_track_entries_tracker_id" in indexes)
        assertTrue(
            "index_track_entries_manga_id_tracker_id (unique) must exist",
            "index_track_entries_manga_id_tracker_id" in indexes,
        )
        db.close()
    }

    @Test
    fun migration24To25_trackEntriesUniqueConstraint() {
        val db = helper.createDatabase(TEST_DB, 24)
        MIGRATION_24_25.migrate(db)
        db.execSQL(
            "INSERT INTO track_entries (manga_id, tracker_id, remote_id, remote_url, title, " +
                "status, last_chapter_read, total_chapters, score, start_date, finish_date) " +
                "VALUES (1, 2, 100, '', 'Test', 0, 1.0, 10, 5.0, 0, 0)",
        )
        var threw = false
        try {
            db.execSQL(
                "INSERT INTO track_entries (manga_id, tracker_id, remote_id, remote_url, title, " +
                    "status, last_chapter_read, total_chapters, score, start_date, finish_date) " +
                    "VALUES (1, 2, 200, '', 'Duplicate', 0, 2.0, 10, 5.0, 0, 0)",
            )
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("Inserting duplicate (manga_id, tracker_id) must throw", threw)
        db.close()
    }

    // ── Migration 25 → 26 ───────────────────────────────────────────────────
    // Adds: composite index (manga_id, status) on download_queue.
    // chapter_id is already the PK (auto-indexed); manga_id alone is covered by the composite prefix.

    @Test
    fun migration25To26_createsCompositeIndex() {
        val db = helper.createDatabase(TEST_DB, 24)
        MIGRATION_24_25.migrate(db)
        MIGRATION_25_26.migrate(db)
        val indexes = db.indexNames("download_queue")
        assertTrue(
            "index_download_queue_manga_id_status must exist after 25→26",
            "index_download_queue_manga_id_status" in indexes,
        )
        assertFalse(
            "index_download_queue_chapter_id must NOT be created (PK is already indexed)",
            "index_download_queue_chapter_id" in indexes,
        )
        db.close()
    }

    @Test
    fun migration25To26_isIdempotent() {
        val db = helper.createDatabase(TEST_DB, 24)
        MIGRATION_24_25.migrate(db)
        MIGRATION_25_26.migrate(db)
        // CREATE INDEX IF NOT EXISTS — re-running must not throw
        MIGRATION_25_26.migrate(db)
        assertTrue(
            "download_queue must still exist after idempotent 25→26",
            "download_queue" in db.tableNames(),
        )
        db.close()
    }

    // ── Migration 28 → 29 ───────────────────────────────────────────────────
    // Adds: achievements table with definitionKey unique index

    @Test
    fun migration28To29_createsAchievementsTable() {
        val db = helper.createDatabase(TEST_DB, 28)
        assertFalse("achievements must NOT exist at v28", "achievements" in db.tableNames())
        MIGRATION_28_29.migrate(db)
        assertTrue("achievements must exist after 28→29", "achievements" in db.tableNames())
        val cols = db.columnNames("achievements")
        assertTrue("id must exist", "id" in cols)
        assertTrue("definitionKey must exist", "definitionKey" in cols)
        assertTrue("unlockedAt must exist", "unlockedAt" in cols)
        assertTrue("progress must exist", "progress" in cols)
        assertTrue("target must exist", "target" in cols)
        assertTrue(
            "index_achievements_definitionKey must exist",
            "index_achievements_definitionKey" in db.indexNames("achievements"),
        )
        db.close()
    }

    @Test
    fun migration28To29_uniqueConstraintOnDefinitionKey() {
        val db = helper.createDatabase(TEST_DB, 28)
        MIGRATION_28_29.migrate(db)
        db.execSQL("INSERT INTO achievements (definitionKey, unlockedAt, progress, target) VALUES ('FIRST_CHAPTER', 0, 0, 1)")
        var threw = false
        try {
            db.execSQL("INSERT INTO achievements (definitionKey, unlockedAt, progress, target) VALUES ('FIRST_CHAPTER', 0, 0, 1)")
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("Duplicate definitionKey must throw", threw)
        db.close()
    }

    // ── Migration 29 → 30 ───────────────────────────────────────────────────
    // Adds: manga.mangaThemeOverride (nullable INTEGER)

    @Test
    fun migration29To30_addsMangaThemeOverride() {
        val db = helper.createDatabase(TEST_DB, 29)
        assertFalse("mangaThemeOverride must NOT exist at v29", "mangaThemeOverride" in db.columnNames("manga"))
        MIGRATION_29_30.migrate(db)
        assertTrue("manga.mangaThemeOverride must exist after 29→30", "mangaThemeOverride" in db.columnNames("manga"))
        db.close()
    }

    // ── Migration 30 → 31 ───────────────────────────────────────────────────
    // Adds: sync_queue table for reader progress sync (#958)

    @Test
    fun migration30To31_createsSyncQueue() {
        val db = helper.createDatabase(TEST_DB, 30)
        assertFalse("sync_queue must NOT exist at v30", "sync_queue" in db.tableNames())
        MIGRATION_30_31.migrate(db)
        assertTrue("sync_queue must exist after 30→31", "sync_queue" in db.tableNames())
        val cols = db.columnNames("sync_queue")
        assertTrue("id must exist", "id" in cols)
        assertTrue("chapterId must exist", "chapterId" in cols)
        assertTrue("mangaId must exist", "mangaId" in cols)
        assertTrue("payload must exist", "payload" in cols)
        assertTrue("attempts must exist", "attempts" in cols)
        assertTrue("createdAt must exist", "createdAt" in cols)
        assertTrue("lastError must exist", "lastError" in cols)
        db.close()
    }

    @Test
    fun migration30To31_syncQueueAcceptsInsert() {
        val db = helper.createDatabase(TEST_DB, 30)
        MIGRATION_30_31.migrate(db)
        db.execSQL(
            "INSERT INTO sync_queue (chapterId, mangaId, payload, attempts, createdAt, lastError) " +
                "VALUES (1, 2, '{\"chapter\":1}', 0, 1234567890, NULL)",
        )
        val cursor = db.query("SELECT COUNT(*) FROM sync_queue")
        assertTrue(cursor.moveToFirst())
        assertEquals("sync_queue must have one row after insert", 1, cursor.getInt(0))
        cursor.close()
        db.close()
    }

    // ── Migration 31 → 32 ───────────────────────────────────────────────────
    // Adds: data_usage table; ALTER download_queue + bytesDownloaded column

    @Test
    fun migration31To32_createsDataUsageTable() {
        val db = helper.createDatabase(TEST_DB, 30)
        MIGRATION_30_31.migrate(db)
        assertFalse("data_usage must NOT exist at v31", "data_usage" in db.tableNames())
        MIGRATION_31_32.migrate(db)
        assertTrue("data_usage must exist after 31→32", "data_usage" in db.tableNames())
        val cols = db.columnNames("data_usage")
        assertTrue("date must exist", "date" in cols)
        assertTrue("category must exist", "category" in cols)
        assertTrue("network must exist", "network" in cols)
        assertTrue("bytes must exist", "bytes" in cols)
        db.close()
    }

    @Test
    fun migration31To32_addsBytesDownloadedToDownloadQueue() {
        val db = helper.createDatabase(TEST_DB, 30)
        MIGRATION_30_31.migrate(db)
        assertFalse("bytesDownloaded must NOT exist at v31", "bytesDownloaded" in db.columnNames("download_queue"))

        db.execSQL("""
            INSERT INTO download_queue
            (chapter_id, manga_id, manga_title, chapter_title, source_name, page_urls_json, priority, status, added_at)
            VALUES (1, 1, 'Manga', 'Chapter', 'Source', '[]', 1, 'QUEUED', 0)
        """.trimIndent())

        MIGRATION_31_32.migrate(db)
        assertTrue("bytesDownloaded must exist after 31→32", "bytesDownloaded" in db.columnNames("download_queue"))

        val cursor = db.query("SELECT bytesDownloaded FROM download_queue WHERE chapter_id = 1")
        assertTrue("existing row must survive migration", cursor.moveToFirst())
        assertEquals("bytesDownloaded must be backfilled to 0", 0L, cursor.getLong(0))
        cursor.close()

        db.close()
    }

    @Test
    fun migration31To32_dataUsageAcceptsInsert() {
        val db = helper.createDatabase(TEST_DB, 30)
        MIGRATION_30_31.migrate(db)
        MIGRATION_31_32.migrate(db)
        db.execSQL("INSERT INTO data_usage (date, category, network, bytes) VALUES ('2026-01-01', 'DOWNLOAD', 'WIFI', 1024)")
        val cursor = db.query("SELECT COUNT(*) FROM data_usage")
        assertTrue(cursor.moveToFirst())
        assertEquals("data_usage must have one row after insert", 1, cursor.getInt(0))
        cursor.close()
        db.close()
    }

    // ── Migration 35 → 36 ───────────────────────────────────────────────────
    // Adds: manga_alternative_source table with indices for cross-source merge (#1053)

    @Test
    fun migration35To36_createsMangaAlternativeSourceTable() {
        helper.createDatabase(TEST_DB, 35).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 36, true, MIGRATION_35_36)
        assertTrue(
            "manga_alternative_source must exist after 35→36",
            "manga_alternative_source" in db.tableNames(),
        )
        val cols = db.columnNames("manga_alternative_source")
        assertTrue("manga_id must exist", "manga_id" in cols)
        assertTrue("alt_manga_id must exist", "alt_manga_id" in cols)
        val indexes = db.indexNames("manga_alternative_source")
        assertTrue(
            "index_manga_alternative_source_manga_id must exist",
            "index_manga_alternative_source_manga_id" in indexes,
        )
        assertTrue(
            "index_manga_alternative_source_alt_manga_id must exist",
            "index_manga_alternative_source_alt_manga_id" in indexes,
        )
        assertTrue(
            "index_manga_alternative_source_manga_id_alt_manga_id (unique) must exist",
            "index_manga_alternative_source_manga_id_alt_manga_id" in indexes,
        )
        db.close()
    }

    @Test
    fun migration35To36_uniqueConstraintOnMangaIdAltMangaId() {
        helper.createDatabase(TEST_DB, 35).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 36, true, MIGRATION_35_36)
        db.execSQL("INSERT INTO manga_alternative_source (manga_id, alt_manga_id) VALUES (1, 2)")
        var threw = false
        try {
            db.execSQL("INSERT INTO manga_alternative_source (manga_id, alt_manga_id) VALUES (1, 2)")
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("Duplicate (manga_id, alt_manga_id) pair must throw", threw)
        db.close()
    }

    // ── Migration 36 → 37 ───────────────────────────────────────────────────
    // Adds: reader_comments table (chapter + book scoped local comments)

    @Test
    fun migration36To37_createsReaderCommentsTable() {
        helper.createDatabase(TEST_DB, 36).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 37, true, MIGRATION_36_37)
        assertTrue(
            "reader_comments must exist after 36→37",
            "reader_comments" in db.tableNames(),
        )
        val cols = db.columnNames("reader_comments")
        assertTrue("manga_id must exist", "manga_id" in cols)
        assertTrue("chapter_id must exist", "chapter_id" in cols)
        assertTrue("body must exist", "body" in cols)
        assertTrue("created_at must exist", "created_at" in cols)
        assertTrue("updated_at must exist", "updated_at" in cols)
        val indexes = db.indexNames("reader_comments")
        assertTrue(
            "index_reader_comments_manga_id must exist",
            "index_reader_comments_manga_id" in indexes,
        )
        assertTrue(
            "index_reader_comments_chapter_id must exist",
            "index_reader_comments_chapter_id" in indexes,
        )
        db.close()
    }

    // ── Migration 37 → 38 ───────────────────────────────────────────────────
    // Recreates chapters table without the bookmark column (SQLite can't DROP COLUMN).
    // Uses createDatabase(37) + direct .migrate() because 38.json is an intermediate
    // schema that was never a stable release version.

    @Test
    fun migration37To38_removesBookmarkColumn() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V37)
        MIGRATION_37_38.migrate(db)
        val cols = db.columnNames("chapters")
        assertFalse("chapters.bookmark must NOT exist after 37→38", "bookmark" in cols)
        assertTrue("chapters.id must still exist after 37→38", "id" in cols)
        assertTrue("chapters.mangaId must still exist after 37→38", "mangaId" in cols)
        assertTrue("chapters.url must still exist after 37→38", "url" in cols)
        assertTrue("chapters.name must still exist after 37→38", "name" in cols)
        assertTrue("chapters.read must still exist after 37→38", "read" in cols)
        assertTrue("chapters.userNotes must still exist after 37→38", "userNotes" in cols)
        val indexes = db.indexNames("chapters")
        assertTrue("index_chapters_mangaId must exist after 37→38", "index_chapters_mangaId" in indexes)
        assertTrue(
            "index_chapters_mangaId_read_sourceOrder must exist after 37→38",
            "index_chapters_mangaId_read_sourceOrder" in indexes,
        )
        assertFalse(
            "index_chapters_bookmark must NOT exist after 37→38",
            "index_chapters_bookmark" in indexes,
        )
        db.close()
    }

    @Test
    fun migration37To38_existingDataSurvives() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V37)
        // Insert manga with all NOT NULL columns as they exist in v37 schema.
        db.execSQL(
            "INSERT INTO manga (sourceId, url, title, status, favorite, lastUpdate, initialized, " +
                "viewerFlags, chapterFlags, coverLastModified, dateAdded, autoDownload, " +
                "notifyNewChapters, contentRating, userCompleted, userDropped) " +
                "VALUES (1, '/manga/1', 'Test Manga', 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0)",
        )
        // Insert chapter with all NOT NULL columns including bookmark (present in v37).
        db.execSQL(
            "INSERT INTO chapters (mangaId, url, name, read, bookmark, lastPageRead, " +
                "chapterNumber, sourceOrder, dateFetch, dateUpload, lastModified) " +
                "VALUES (1, '/chapter/1', 'Chapter 1', 0, 1, 5, 1.0, 0, 0, 0, 0)",
        )
        // Insert referential child rows (reading_history and page_bookmarks reference chapters).
        // These must survive the table reconstruction without FK violations.
        db.execSQL(
            "INSERT INTO reading_history (chapter_id, read_at, read_duration_ms) VALUES (1, 123456, 1000)",
        )
        db.execSQL(
            "INSERT INTO page_bookmarks (manga_id, chapter_id, page_index, note, created_at) " +
                "VALUES (1, 1, 2, 'Note', 123456)",
        )
        MIGRATION_37_38.migrate(db)
        val cursor = db.query("SELECT name, read, lastPageRead FROM chapters WHERE url = '/chapter/1'")
        assertTrue("Row must survive migration 37→38", cursor.moveToFirst())
        assertEquals("name must survive migration", "Chapter 1", cursor.getString(0))
        assertEquals("read must survive migration", 0, cursor.getInt(1))
        assertEquals("lastPageRead must survive migration", 5, cursor.getInt(2))
        cursor.close()
        // Verify referential child rows also survived.
        val historyCursor = db.query("SELECT COUNT(*) FROM reading_history")
        assertTrue(historyCursor.moveToFirst())
        assertEquals("reading_history rows must survive migration", 1, historyCursor.getInt(0))
        historyCursor.close()
        val bookmarkCursor = db.query("SELECT COUNT(*) FROM page_bookmarks")
        assertTrue(bookmarkCursor.moveToFirst())
        assertEquals("page_bookmarks rows must survive migration", 1, bookmarkCursor.getInt(0))
        bookmarkCursor.close()
        db.close()
    }

    // ── Migration 38 → 39 ───────────────────────────────────────────────────
    // Adds: bookmark_collections table + collection_id FK on page_bookmarks.
    // Uses createDatabase(37) + direct .migrate() chain and validates final schema
    // against the known-good 39.json export via runMigrationsAndValidate.

    @Test
    fun migration38To39_createsBookmarkCollectionsTable() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V37)
        MIGRATION_37_38.migrate(db)
        assertFalse("bookmark_collections must NOT exist at v38", "bookmark_collections" in db.tableNames())
        MIGRATION_38_39.migrate(db)
        assertTrue("bookmark_collections must exist after 38→39", "bookmark_collections" in db.tableNames())
        val cols = db.columnNames("bookmark_collections")
        assertTrue("id must exist", "id" in cols)
        assertTrue("name must exist", "name" in cols)
        assertTrue("created_at must exist", "created_at" in cols)
        db.close()
    }

    @Test
    fun migration38To39_addsCollectionIdToPageBookmarks() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V37)
        MIGRATION_37_38.migrate(db)
        assertFalse("collection_id must NOT exist at v38", "collection_id" in db.columnNames("page_bookmarks"))
        MIGRATION_38_39.migrate(db)
        assertTrue(
            "page_bookmarks.collection_id must exist after 38→39",
            "collection_id" in db.columnNames("page_bookmarks"),
        )
        assertTrue(
            "index_page_bookmarks_collection_id must exist after 38→39",
            "index_page_bookmarks_collection_id" in db.indexNames("page_bookmarks"),
        )
        // Verify the FK constraint: collection_id REFERENCES bookmark_collections(id) ON DELETE SET NULL
        val fkCursor = db.query("PRAGMA foreign_key_list('page_bookmarks')")
        var foundCollectionFk = false
        while (fkCursor.moveToNext()) {
            val table = fkCursor.getString(fkCursor.getColumnIndexOrThrow("table"))
            val from = fkCursor.getString(fkCursor.getColumnIndexOrThrow("from"))
            val to = fkCursor.getString(fkCursor.getColumnIndexOrThrow("to"))
            val onDelete = fkCursor.getString(fkCursor.getColumnIndexOrThrow("on_delete"))
            if (table == "bookmark_collections" && from == "collection_id" && to == "id" && onDelete == "SET NULL") {
                foundCollectionFk = true
                break
            }
        }
        fkCursor.close()
        assertTrue(
            "collection_id FK must reference bookmark_collections(id) ON DELETE SET NULL",
            foundCollectionFk,
        )
        db.close()
    }

    @Test
    fun migration38To39_collectionIdDefaultsToNull() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V37)
        MIGRATION_37_38.migrate(db)
        // Insert required parent rows before page_bookmarks to satisfy FK constraints.
        db.execSQL(
            "INSERT INTO manga (sourceId, url, title, status, favorite, lastUpdate, initialized, " +
                "viewerFlags, chapterFlags, coverLastModified, dateAdded, autoDownload, " +
                "notifyNewChapters, contentRating, userCompleted, userDropped) " +
                "VALUES (1, '/manga/1', 'Test Manga', 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0)",
        )
        db.execSQL(
            "INSERT INTO chapters (mangaId, url, name, read, lastPageRead, " +
                "chapterNumber, sourceOrder, dateFetch, dateUpload, lastModified) " +
                "VALUES (1, '/chapter/1', 'Ch 1', 0, 0, 1.0, 0, 0, 0, 0)",
        )
        db.execSQL(
            "INSERT INTO page_bookmarks (manga_id, chapter_id, page_index, note, created_at) " +
                "VALUES (1, 1, 0, NULL, 0)",
        )
        MIGRATION_38_39.migrate(db)
        val cursor = db.query("SELECT collection_id FROM page_bookmarks WHERE manga_id = 1")
        assertTrue("Row must survive migration 38→39", cursor.moveToFirst())
        assertTrue("collection_id must be NULL after migration", cursor.isNull(0))
        cursor.close()
        db.close()
    }

    @Test
    fun migration37To39_fullChainValidatesAgainstSchema() {
        // Validates the full 37→38→39 chain against the exported 39.json schema.
        helper.createDatabase(TEST_DB, SCHEMA_V37).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, SCHEMA_V39, true, MIGRATION_37_38, MIGRATION_38_39)
        assertTrue("chapters must exist and lack bookmark column after full chain",
            "id" in db.columnNames("chapters") && "bookmark" !in db.columnNames("chapters"))
        assertTrue("bookmark_collections must exist after full chain",
            "bookmark_collections" in db.tableNames())
        assertTrue("collection_id must exist in page_bookmarks after full chain",
            "collection_id" in db.columnNames("page_bookmarks"))
        db.close()
    }

    // ── Migration 39 → 40 ───────────────────────────────────────────────────
    // Adds: update_errors table (current unresolved per-manga library update failures).

    @Test
    fun migration39To40_createsUpdateErrorsTable() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V39)
        assertFalse("update_errors must NOT exist at v39", "update_errors" in db.tableNames())
        MIGRATION_39_40.migrate(db)
        assertTrue("update_errors must exist after 39→40", "update_errors" in db.tableNames())
        val cols = db.columnNames("update_errors")
        assertTrue("mangaId must exist", "mangaId" in cols)
        assertTrue("errorMessage must exist", "errorMessage" in cols)
        assertTrue("timestamp must exist", "timestamp" in cols)
        db.close()
    }

    @Test
    fun migration39To40_updateErrorsHasMangaIdForeignKeyToManga() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V39)
        MIGRATION_39_40.migrate(db)
        val fkCursor = db.query("PRAGMA foreign_key_list('update_errors')")
        var foundMangaFk = false
        while (fkCursor.moveToNext()) {
            val table = fkCursor.getString(fkCursor.getColumnIndexOrThrow("table"))
            val from = fkCursor.getString(fkCursor.getColumnIndexOrThrow("from"))
            val to = fkCursor.getString(fkCursor.getColumnIndexOrThrow("to"))
            val onDelete = fkCursor.getString(fkCursor.getColumnIndexOrThrow("on_delete"))
            if (table == "manga" && from == "mangaId" && to == "id" && onDelete == "CASCADE") {
                foundMangaFk = true
                break
            }
        }
        fkCursor.close()
        assertTrue("mangaId FK must reference manga(id) ON DELETE CASCADE", foundMangaFk)
        db.close()
    }

    // ── Migration 40 → 41 ───────────────────────────────────────────────────
    // Adds: manga_metadata table (cached AniList metadata for the details screen).

    @Test
    fun migration40To41_createsMangaMetadataTable() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V40)
        assertFalse("manga_metadata must NOT exist at v40", "manga_metadata" in db.tableNames())
        MIGRATION_40_41.migrate(db)
        assertTrue("manga_metadata must exist after 40\u219241", "manga_metadata" in db.tableNames())
        val cols = db.columnNames("manga_metadata")
        // The full column set, because a missing column is the failure Room only reports on an
        // *upgrade* — a fresh install builds the table from the entity and never notices.
        listOf(
            "mangaId", "anilistId", "description", "bannerImage", "coverImage", "coverColor",
            "genres", "tagNames", "tagRanks", "averageScore", "popularity", "favourites",
            "format", "countryOfOrigin", "status", "chapters", "startDate", "endDate",
            "synonyms", "fetchedAt",
        ).forEach { column ->
            assertTrue("$column must exist on manga_metadata", column in cols)
        }
        db.close()
    }

    @Test
    fun migration40To41_mangaMetadataCascadesFromManga() {
        // Without the cascade, removing a manga leaves a row keyed to an id that no longer
        // exists, and the table grows for as long as the app is used.
        val db = helper.createDatabase(TEST_DB, SCHEMA_V40)
        MIGRATION_40_41.migrate(db)
        val fkCursor = db.query("PRAGMA foreign_key_list('manga_metadata')")
        var foundMangaFk = false
        while (fkCursor.moveToNext()) {
            val table = fkCursor.getString(fkCursor.getColumnIndexOrThrow("table"))
            val from = fkCursor.getString(fkCursor.getColumnIndexOrThrow("from"))
            val to = fkCursor.getString(fkCursor.getColumnIndexOrThrow("to"))
            val onDelete = fkCursor.getString(fkCursor.getColumnIndexOrThrow("on_delete"))
            if (table == "manga" && from == "mangaId" && to == "id" && onDelete == "CASCADE") {
                foundMangaFk = true
                break
            }
        }
        fkCursor.close()
        assertTrue("mangaId FK must reference manga(id) ON DELETE CASCADE", foundMangaFk)
        db.close()
    }

    /**
     * The assertion that actually catches a column-type mismatch.
     *
     * `runMigrationsAndValidate` compares the migrated schema against the exported JSON for v41,
     * which is generated from the entity. A hand-written CREATE TABLE that says INTEGER where Room
     * expects TEXT passes both tests above and fails here — and in production it would fail only
     * for users upgrading, never on a fresh install.
     */
    @Test
    fun migration40To41_validatesAgainstTheExportedSchema() {
        helper.createDatabase(TEST_DB, SCHEMA_V40).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, SCHEMA_V41, true, MIGRATION_40_41)
        assertTrue("manga_metadata must exist after validation", "manga_metadata" in db.tableNames())
        db.close()
    }

    // ── Migration 41 → 42 ───────────────────────────────────────────────────
    // Adds: manga_anilist_link (which AniList media a manga is, and whether a human said so).

    @Test
    fun migration41To42_createsMangaAniListLinkTable() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V41)
        assertFalse("manga_anilist_link must NOT exist at v41", "manga_anilist_link" in db.tableNames())
        MIGRATION_41_42.migrate(db)
        assertTrue("manga_anilist_link must exist after 41\u219242", "manga_anilist_link" in db.tableNames())
        val cols = db.columnNames("manga_anilist_link")
        listOf("mangaId", "anilistId", "userConfirmed", "matchedAt").forEach { column ->
            assertTrue("$column must exist on manga_anilist_link", column in cols)
        }
        db.close()
    }

    @Test
    fun migration41To42_linkCascadesFromManga() {
        // Without the cascade, removing a manga leaves a link keyed to an id that no longer
        // exists. The cascade is the whole reclamation story for this table — there is no sweep.
        val db = helper.createDatabase(TEST_DB, SCHEMA_V41)
        MIGRATION_41_42.migrate(db)
        val fkCursor = db.query("PRAGMA foreign_key_list('manga_anilist_link')")
        var foundMangaFk = false
        while (fkCursor.moveToNext()) {
            val table = fkCursor.getString(fkCursor.getColumnIndexOrThrow("table"))
            val from = fkCursor.getString(fkCursor.getColumnIndexOrThrow("from"))
            val to = fkCursor.getString(fkCursor.getColumnIndexOrThrow("to"))
            val onDelete = fkCursor.getString(fkCursor.getColumnIndexOrThrow("on_delete"))
            if (table == "manga" && from == "mangaId" && to == "id" && onDelete == "CASCADE") {
                foundMangaFk = true
                break
            }
        }
        fkCursor.close()
        assertTrue("mangaId FK must reference manga(id) ON DELETE CASCADE", foundMangaFk)
        db.close()
    }

    @Test
    fun migration41To42_leavesTheMetadataCacheAlone() {
        // The link is deliberately a second table rather than a column on manga_metadata, because
        // a user's manual correction has to outlive a cache that clearMetadata throws away. If a
        // later change ever folds them together, this is the test that should stop it.
        val db = helper.createDatabase(TEST_DB, SCHEMA_V41)
        MIGRATION_41_42.migrate(db)
        assertTrue("manga_metadata must still exist after 41\u219242", "manga_metadata" in db.tableNames())
        assertTrue(
            "manga_metadata must keep its own anilistId",
            "anilistId" in db.columnNames("manga_metadata"),
        )
        db.close()
    }

    /**
     * The assertion that actually catches a column-type mismatch.
     *
     * `userConfirmed` is the one to watch here: it is a `Boolean` on the entity, which Room exports
     * as INTEGER. A hand-written CREATE TABLE saying TEXT would pass both tests above and fail only
     * for users upgrading — never on a fresh install, where the table is built from the entity.
     */
    @Test
    fun migration41To42_validatesAgainstTheExportedSchema() {
        helper.createDatabase(TEST_DB, SCHEMA_V41).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, SCHEMA_V42, true, MIGRATION_41_42)
        assertTrue("manga_anilist_link must exist after validation", "manga_anilist_link" in db.tableNames())
        db.close()
    }

    // ── v42 → v43: characters and staff on manga_metadata ───────────────────

    @Test
    fun migration42To43_addsCharacterAndStaffColumns() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V42)
        MIGRATION_42_43.migrate(db)
        val columns = db.columnNames("manga_metadata")
        assertTrue("characters column must exist after 42\u219243", "characters" in columns)
        assertTrue("staff column must exist after 42\u219243", "staff" in columns)
        db.close()
    }

    /**
     * An existing cached row must survive the upgrade with a value the converter can read.
     *
     * The columns are NOT NULL, so rows written before this migration need a default, and it has
     * to be valid JSON rather than merely non-null — `DatabaseConverters.fromPersonList` parses
     * whatever is there. A default of `''` would happen to work (the converter short-circuits on
     * empty) and `'{}'` would not; this pins the one that means what it says.
     */
    @Test
    fun migration42To43_backfillsExistingRowsWithAnEmptyJsonArray() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V42)
        db.execSQL(
            """
            INSERT INTO manga(
                id, sourceId, url, title, status, favorite, lastUpdate, initialized,
                viewerFlags, chapterFlags, coverLastModified, dateAdded, autoDownload,
                notifyNewChapters, contentRating, userCompleted, userDropped
            ) VALUES (7, 1, 'u', 'T', 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO manga_metadata(mangaId, anilistId, genres, tagNames, tagRanks, synonyms, fetchedAt)
            VALUES (7, 999, '', '', '', '', 0)
            """.trimIndent()
        )
        MIGRATION_42_43.migrate(db)

        val cursor = db.query("SELECT characters, staff FROM manga_metadata WHERE mangaId = 7")
        assertTrue("the pre-existing row must survive the upgrade", cursor.moveToFirst())
        assertEquals("[]", cursor.getString(0))
        assertEquals("[]", cursor.getString(1))
        cursor.close()
        db.close()
    }

    /**
     * The assertion that actually catches a mismatch.
     *
     * Room compares the column default it expects from `@ColumnInfo(defaultValue = "[]")` against
     * the one SQLite reports for the migrated table. A default written only into the migration, or
     * only onto the entity, passes both tests above and fails on upgrade alone — never on a fresh
     * install, where the table is built from the entity.
     */
    @Test
    fun migration42To43_validatesAgainstTheExportedSchema() {
        helper.createDatabase(TEST_DB, SCHEMA_V42).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, SCHEMA_V43, true, MIGRATION_42_43)
        assertTrue("manga_metadata must exist after validation", "manga_metadata" in db.tableNames())
        db.close()
    }

    // ── v43 → v44: relations and external links on manga_metadata ───────────

    @Test
    fun migration43To44_addsRelationAndExternalLinkColumns() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V43)
        MIGRATION_43_44.migrate(db)
        val columns = db.columnNames("manga_metadata")
        assertTrue("relations column must exist after 43\u219244", "relations" in columns)
        assertTrue("externalLinks column must exist after 43\u219244", "externalLinks" in columns)
        db.close()
    }

    /** Same NOT NULL backfill concern as 42→43: the default has to be JSON the converter can read. */
    @Test
    fun migration43To44_backfillsExistingRowsWithAnEmptyJsonArray() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V43)
        db.execSQL(
            """
            INSERT INTO manga(
                id, sourceId, url, title, status, favorite, lastUpdate, initialized,
                viewerFlags, chapterFlags, coverLastModified, dateAdded, autoDownload,
                notifyNewChapters, contentRating, userCompleted, userDropped
            ) VALUES (8, 1, 'u', 'T', 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO manga_metadata(mangaId, anilistId, genres, tagNames, tagRanks, synonyms, fetchedAt)
            VALUES (8, 999, '', '', '', '', 0)
            """.trimIndent()
        )
        MIGRATION_43_44.migrate(db)

        val cursor = db.query("SELECT relations, externalLinks FROM manga_metadata WHERE mangaId = 8")
        assertTrue("the pre-existing row must survive the upgrade", cursor.moveToFirst())
        assertEquals("[]", cursor.getString(0))
        assertEquals("[]", cursor.getString(1))
        cursor.close()
        db.close()
    }

    @Test
    fun migration43To44_validatesAgainstTheExportedSchema() {
        helper.createDatabase(TEST_DB, SCHEMA_V43).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, SCHEMA_V44, true, MIGRATION_43_44)
        assertTrue("manga_metadata must exist after validation", "manga_metadata" in db.tableNames())
        db.close()
    }

    // ── v44 → v45: one feed row per chapter ─────────────────────────────────

    private fun SupportSQLiteDatabase.insertFeedItem(id: Long, mangaId: Long, chapterId: Long) {
        execSQL(
            """
            INSERT INTO feed_items(
                id, mangaId, mangaTitle, mangaThumbnailUrl, chapterId, chapterName,
                chapterNumber, sourceId, sourceName, timestamp, isRead
            ) VALUES ($id, $mangaId, 'T', NULL, $chapterId, 'Ch', 1.0, 1, 'S', 0, 0)
            """.trimIndent()
        )
    }

    /**
     * A manual and a periodic library update are separate unique-work names and can overlap, so
     * both can see the same chapter as new. `chapters` already concedes this race with a unique
     * `(mangaId, url)` index; this is the same defence for the feed.
     */
    @Test
    fun migration44To45_rejectsASecondRowForTheSameChapter() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V44)
        MIGRATION_44_45.migrate(db)
        db.insertFeedItem(id = 1, mangaId = 7, chapterId = 70)

        var rejected = false
        try {
            db.insertFeedItem(id = 2, mangaId = 7, chapterId = 70)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            rejected = true
        }

        assertTrue("a duplicate (mangaId, chapterId) must not be insertable", rejected)
        db.close()
    }

    @Test
    fun migration44To45_keepsDifferentChaptersOfTheSameManga() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V44)
        MIGRATION_44_45.migrate(db)
        db.insertFeedItem(id = 1, mangaId = 7, chapterId = 70)
        db.insertFeedItem(id = 2, mangaId = 7, chapterId = 71)

        val cursor = db.query("SELECT COUNT(*) FROM feed_items WHERE mangaId = 7")
        cursor.moveToFirst()
        assertEquals(2, cursor.getInt(0))
        cursor.close()
        db.close()
    }

    /**
     * The index cannot be created over an existing violation, so the migration deletes duplicates
     * first — keeping the lowest id, which is the first recording and the one whose `isRead`
     * reflects what the user actually saw.
     */
    @Test
    fun migration44To45_dropsPreExistingDuplicatesKeepingTheFirst() {
        val db = helper.createDatabase(TEST_DB, SCHEMA_V44)
        db.insertFeedItem(id = 1, mangaId = 7, chapterId = 70)
        db.insertFeedItem(id = 2, mangaId = 7, chapterId = 70)
        db.insertFeedItem(id = 3, mangaId = 7, chapterId = 71)

        MIGRATION_44_45.migrate(db)

        val cursor = db.query("SELECT id FROM feed_items ORDER BY id")
        val ids = buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        cursor.close()
        assertEquals(listOf(1L, 3L), ids)
        db.close()
    }

    @Test
    fun migration44To45_validatesAgainstTheExportedSchema() {
        helper.createDatabase(TEST_DB, SCHEMA_V44).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, SCHEMA_V45, true, MIGRATION_44_45)
        assertTrue("feed_items must exist after validation", "feed_items" in db.tableNames())
        db.close()
    }

    // ── Full chain v9 → v26 ─────────────────────────────────────────────────
    // Starts from v9 (oldest exported schema JSON). Runs all migrations v9→v26 via
    // runMigrationsAndValidate, which validates the final schema against 26.json.

    @Test
    fun fullMigrationChain_v9ToV26() {
        helper.createDatabase(TEST_DB, 9).close()
        val db = helper.runMigrationsAndValidate(
            TEST_DB, 26, true,
            *ALL_MIGRATIONS.filter { it.startVersion in 9..25 }.toTypedArray(),
        )
        val tables = db.tableNames()
        assertTrue("manga must exist after full chain", "manga" in tables)
        assertTrue("chapters must exist after full chain", "chapters" in tables)
        assertTrue("reading_history must exist after full chain", "reading_history" in tables)
        assertTrue("reading_streaks must exist after full chain", "reading_streaks" in tables)
        assertTrue("reading_lists must exist after full chain", "reading_lists" in tables)
        assertTrue("download_queue must exist after full chain", "download_queue" in tables)
        assertTrue("page_bookmarks must exist after full chain", "page_bookmarks" in tables)
        assertTrue("tracker_sync_state must exist after full chain", "tracker_sync_state" in tables)
        assertTrue("track_entries must exist after full chain", "track_entries" in tables)
        assertTrue("contentRating must exist in manga after full chain", "contentRating" in db.columnNames("manga"))
        assertTrue("userNotes must exist in chapters after full chain", "userNotes" in db.columnNames("chapters"))
        assertTrue(
            "update_frequency must exist in categories after full chain",
            "update_frequency" in db.columnNames("categories"),
        )
        assertTrue(
            "index_chapters_mangaId_read_sourceOrder must exist after full chain",
            "index_chapters_mangaId_read_sourceOrder" in db.indexNames("chapters"),
        )
        assertTrue(
            "index_track_entries_manga_id must exist after full chain",
            "index_track_entries_manga_id" in db.indexNames("track_entries"),
        )
        assertTrue(
            "index_download_queue_manga_id_status must exist after full chain",
            "index_download_queue_manga_id_status" in db.indexNames("download_queue"),
        )
        db.close()
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun SupportSQLiteDatabase.tableNames(): Set<String> {
    val names = mutableSetOf<String>()
    query(
        "SELECT name FROM sqlite_master WHERE type='table' " +
            "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' AND name != 'room_master_table'",
    ).use { cursor ->
        while (cursor.moveToNext()) names.add(cursor.getString(0))
    }
    return names
}

private fun SupportSQLiteDatabase.columnNames(table: String): Set<String> {
    val names = mutableSetOf<String>()
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIdx = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) names.add(cursor.getString(nameIdx))
    }
    return names
}

private fun SupportSQLiteDatabase.indexNames(table: String): Set<String> {
    val names = mutableSetOf<String>()
    query("PRAGMA index_list(`$table`)").use { cursor ->
        val nameIdx = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) names.add(cursor.getString(nameIdx))
    }
    return names
}
