package app.otakureader.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.migrations.ALL_MIGRATIONS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private lateinit var database: OtakuReaderDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java
        )
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun allMigrations_formContiguousChain() {
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
                next.startVersion
            )
        }
    }

    @Test
    fun allMigrations_eachVersionIncrementsByOne() {
        for (migration in ALL_MIGRATIONS) {
            assertEquals(
                "Migration ${migration.startVersion}→${migration.endVersion} should increment by 1",
                migration.startVersion + 1,
                migration.endVersion
            )
        }
    }

    @Test
    fun allMigrations_count() {
        // v2→v14 is 12 steps
        assertEquals("Expected 12 migrations (v2→v14)", 12, ALL_MIGRATIONS.size)
    }

    @Test
    fun database_opensAtVersion14() {
        val version = database.openHelper.readableDatabase.version
        assertEquals("Database should open at version 14", 14, version)
    }

    @Test
    fun database_coreTablesExist() {
        val db = database.openHelper.readableDatabase
        val expectedTables = listOf(
            "manga",
            "chapters",
            "categories",
            "manga_categories",
            "reading_history",
            "opds_servers",
            "feed_items",
            "feed_sources",
            "feed_saved_searches",
            "tracker_sync_state",
            "sync_configuration",
            "categorization_results",
            "smart_search_cache",
            "recommendations",
            "reading_patterns",
            "recommendation_refreshes"
        )

        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' AND name != 'room_master_table'"
        )
        val actualTables = mutableSetOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                actualTables.add(it.getString(0))
            }
        }

        for (table in expectedTables) {
            assertTrue("Table '$table' must exist after all migrations", actualTables.contains(table))
        }
    }

    @Test
    fun database_mangaTable_hasContentRatingColumn() {
        val db = database.openHelper.readableDatabase
        val cursor = db.query("PRAGMA table_info(`manga`)")
        val columns = mutableSetOf<String>()
        cursor.use {
            val nameIdx = it.getColumnIndex("name")
            while (it.moveToNext()) {
                columns.add(it.getString(nameIdx))
            }
        }
        assertTrue("manga.contentRating column must exist (added in v14)", columns.contains("contentRating"))
        assertTrue("manga.autoDownload column must exist (added in v4)", columns.contains("autoDownload"))
        assertTrue("manga.notes column must exist (added in v5)", columns.contains("notes"))
        assertTrue("manga.readerDirection column must exist (added in v8)", columns.contains("readerDirection"))
    }
}
