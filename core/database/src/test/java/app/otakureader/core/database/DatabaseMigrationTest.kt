package app.otakureader.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.otakureader.core.database.migrations.ALL_MIGRATIONS
import app.otakureader.core.database.migrations.MIGRATION_13_14
import app.otakureader.core.database.migrations.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
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
        assertEquals("Migration chain must end at version 14", 14, sorted.last().endVersion)

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
        assertEquals("Expected 12 migrations (v2→v14)", 12, ALL_MIGRATIONS.size)
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
