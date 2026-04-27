package app.otakureader.data.backup

import app.otakureader.data.backup.mapper.toBackupCategory
import app.otakureader.data.backup.mapper.toBackupChapter
import app.otakureader.data.backup.mapper.toBackupManga
import app.otakureader.data.backup.mapper.toBackupReadingHistory
import app.otakureader.data.backup.mapper.toCategoryEntity
import app.otakureader.data.backup.mapper.toChapterEntity
import app.otakureader.data.backup.mapper.toMangaEntity
import app.otakureader.data.backup.model.BackupCategory
import app.otakureader.data.backup.model.BackupChapter
import app.otakureader.data.backup.model.BackupData
import app.otakureader.data.backup.model.BackupFeedSavedSearch
import app.otakureader.data.backup.model.BackupFeedSource
import app.otakureader.data.backup.model.BackupManga
import app.otakureader.data.backup.model.BackupOpdsServer
import app.otakureader.data.backup.model.BackupPreferences
import app.otakureader.data.backup.model.BackupReadingHistory
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.ReadingHistoryEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Round-trip tests: BackupData JSON serialization ↔ deserialization, and
 * mapper fidelity between domain backup models and Room entities.
 */
class BackupRoundTripTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // ── BackupData JSON round-trip ───────────────────────────────────────────

    @Test
    fun `BackupData serialises and deserialises with correct version`() {
        val original = BackupData(version = BackupData.CURRENT_VERSION)
        val json = json.encodeToString(original)
        val restored = this.json.decodeFromString<BackupData>(json)
        assertEquals(BackupData.CURRENT_VERSION, restored.version)
    }

    @Test
    fun `BackupData with manga round-trips through JSON`() {
        val chapter = BackupChapter(
            url = "/ch/1",
            name = "Chapter 1",
            read = true,
            bookmark = false,
            lastPageRead = 7,
            chapterNumber = 1f,
            readingHistory = BackupReadingHistory(readAt = 1_000_000L, readDurationMs = 60_000L),
        )
        val manga = BackupManga(
            sourceId = 42L,
            url = "/m/1",
            title = "Test Manga",
            author = "Author",
            genre = listOf("Action", "Comedy"),
            favorite = true,
            notes = "Good series",
            contentRating = 1,
            chapters = listOf(chapter),
            categoryIds = listOf(2L),
        )
        val original = BackupData(manga = listOf(manga))
        val json = json.encodeToString(original)
        val restored = this.json.decodeFromString<BackupData>(json)

        assertEquals(1, restored.manga.size)
        val rm = restored.manga[0]
        assertEquals(manga.sourceId, rm.sourceId)
        assertEquals(manga.title, rm.title)
        assertEquals(manga.author, rm.author)
        assertEquals(manga.genre, rm.genre)
        assertEquals(manga.notes, rm.notes)
        assertEquals(manga.contentRating, rm.contentRating)
        assertEquals(1, rm.chapters.size)
        val rc = rm.chapters[0]
        assertEquals(chapter.url, rc.url)
        assertEquals(chapter.read, rc.read)
        assertEquals(chapter.lastPageRead, rc.lastPageRead)
        assertNotNull(rc.readingHistory)
        assertEquals(1_000_000L, rc.readingHistory!!.readAt)
        assertEquals(60_000L, rc.readingHistory.readDurationMs)
    }

    @Test
    fun `BackupData with categories round-trips through JSON`() {
        val categories = listOf(
            BackupCategory(id = 1L, name = "Reading", order = 0),
            BackupCategory(id = 2L, name = "Completed", order = 1, flags = 3),
        )
        val original = BackupData(categories = categories)
        val json = json.encodeToString(original)
        val restored = this.json.decodeFromString<BackupData>(json)

        assertEquals(2, restored.categories.size)
        assertEquals("Reading", restored.categories[0].name)
        assertEquals(3, restored.categories[1].flags)
    }

    @Test
    fun `BackupData with preferences round-trips through JSON`() {
        val prefs = BackupPreferences(
            themeMode = 2,
            useDynamicColor = false,
            locale = "ja",
            readerMode = 1,
            keepScreenOn = true,
            volumeKeysEnabled = true,
            libraryGridSize = 4,
            updateCheckInterval = 6,
        )
        val original = BackupData(preferences = prefs)
        val json = json.encodeToString(original)
        val restored = this.json.decodeFromString<BackupData>(json)

        assertNotNull(restored.preferences)
        val rp = restored.preferences!!
        assertEquals(2, rp.themeMode)
        assertEquals(false, rp.useDynamicColor)
        assertEquals("ja", rp.locale)
        assertEquals(4, rp.libraryGridSize)
        assertEquals(6, rp.updateCheckInterval)
    }

    @Test
    fun `BackupData with OPDS servers round-trips through JSON`() {
        val original = BackupData(
            opdsServers = listOf(
                BackupOpdsServer(id = 1L, name = "My OPDS", url = "https://opds.example.com"),
            )
        )
        val json = json.encodeToString(original)
        val restored = this.json.decodeFromString<BackupData>(json)
        assertEquals(1, restored.opdsServers.size)
        assertEquals("My OPDS", restored.opdsServers[0].name)
    }

    @Test
    fun `BackupData with feed saved searches round-trips through JSON`() {
        val original = BackupData(
            feedSavedSearches = listOf(
                BackupFeedSavedSearch(
                    id = 10L, sourceId = 99L, sourceName = "MangaDex",
                    query = "isekai", filtersJson = """{"genre":["Action"]}""", order = 0,
                )
            )
        )
        val json = json.encodeToString(original)
        val restored = this.json.decodeFromString<BackupData>(json)
        assertEquals(1, restored.feedSavedSearches.size)
        assertEquals("isekai", restored.feedSavedSearches[0].query)
        assertEquals("""{"genre":["Action"]}""", restored.feedSavedSearches[0].filtersJson)
    }

    @Test
    fun `BackupData ignores unknown keys in JSON`() {
        val json = """{"version":2,"unknownField":"value","manga":[]}"""
        val restored = this.json.decodeFromString<BackupData>(json)
        assertEquals(2, restored.version)
        assertEquals(0, restored.manga.size)
    }

    // ── Mapper round-trips ───────────────────────────────────────────────────

    @Test
    fun `MangaEntity toBackupManga toMangaEntity preserves scalar fields`() {
        val entity = MangaEntity(
            id = 5L,
            sourceId = 100L,
            url = "/m/1",
            title = "Round-trip Manga",
            author = "AuthorA",
            artist = "ArtistB",
            description = "Desc",
            genre = "Action|||Comedy|||Drama",
            status = 2,
            favorite = true,
            lastUpdate = 9999L,
            initialized = true,
            viewerFlags = 3,
            chapterFlags = 7,
            coverLastModified = 12345L,
            dateAdded = 54321L,
            autoDownload = false,
            notes = "Notes here",
            readerBackgroundColor = 0xFF000000L,
            contentRating = 1,
        )

        val backup = entity.toBackupManga()
        val restored = backup.toMangaEntity()

        // id is reset to 0 (Room auto-generates)
        assertEquals(0L, restored.id)
        assertEquals(entity.sourceId, restored.sourceId)
        assertEquals(entity.url, restored.url)
        assertEquals(entity.title, restored.title)
        assertEquals(entity.author, restored.author)
        assertEquals(entity.description, restored.description)
        assertEquals(entity.status, restored.status)
        assertEquals(entity.favorite, restored.favorite)
        assertEquals(entity.notes, restored.notes)
        assertEquals(entity.readerBackgroundColor, restored.readerBackgroundColor)
        assertEquals(entity.contentRating, restored.contentRating)
        // Genre is serialised as "|||"-joined string
        assertEquals(entity.genre, restored.genre)
    }

    @Test
    fun `MangaEntity genre list survives BackupManga round-trip`() {
        val entity = MangaEntity(
            id = 1L, sourceId = 1L, url = "/m/1", title = "T",
            genre = "Action|||Romance|||Isekai",
            status = 0, favorite = false, lastUpdate = 0L, initialized = false,
            viewerFlags = 0, chapterFlags = 0, coverLastModified = 0L, dateAdded = 0L,
            autoDownload = false,
        )
        val backup = entity.toBackupManga()
        assertEquals(listOf("Action", "Romance", "Isekai"), backup.genre)

        val restored = backup.toMangaEntity()
        assertEquals("Action|||Romance|||Isekai", restored.genre)
    }

    @Test
    fun `ChapterEntity toBackupChapter toChapterEntity preserves reading state`() {
        val entity = ChapterEntity(
            id = 20L,
            mangaId = 5L,
            url = "/ch/20",
            name = "Chapter 20",
            scanlator = "Group X",
            read = true,
            bookmark = true,
            lastPageRead = 15,
            chapterNumber = 20f,
            sourceOrder = 19,
            dateFetch = 111L,
            dateUpload = 222L,
            lastModified = 333L,
        )

        val backup = entity.toBackupChapter()
        val restoredMangaId = 7L
        val restored = backup.toChapterEntity(restoredMangaId)

        assertEquals(0L, restored.id) // auto-generated
        assertEquals(restoredMangaId, restored.mangaId)
        assertEquals(entity.url, restored.url)
        assertEquals(entity.name, restored.name)
        assertEquals(entity.scanlator, restored.scanlator)
        assertEquals(entity.read, restored.read)
        assertEquals(entity.bookmark, restored.bookmark)
        assertEquals(entity.lastPageRead, restored.lastPageRead)
        assertEquals(entity.chapterNumber, restored.chapterNumber)
        assertEquals(entity.dateFetch, restored.dateFetch)
        assertEquals(entity.dateUpload, restored.dateUpload)
    }

    @Test
    fun `ReadingHistoryEntity toBackupReadingHistory preserves timestamps`() {
        val entity = ReadingHistoryEntity(
            id = 1L, chapterId = 10L, readAt = 5_000_000L, readDurationMs = 120_000L,
        )
        val backup = entity.toBackupReadingHistory()
        assertEquals(5_000_000L, backup.readAt)
        assertEquals(120_000L, backup.readDurationMs)
    }

    @Test
    fun `CategoryEntity toBackupCategory toCategoryEntity preserves all fields`() {
        val entity = CategoryEntity(id = 3L, name = "Plan to Read", order = 2, flags = 5)
        val backup = entity.toBackupCategory()
        val restored = backup.toCategoryEntity()

        assertEquals(3L, restored.id)
        assertEquals("Plan to Read", restored.name)
        assertEquals(2, restored.order)
        assertEquals(5, restored.flags)
    }

    @Test
    fun `BackupManga with null optional fields round-trips without errors`() {
        val manga = BackupManga(
            sourceId = 1L,
            url = "/m/null",
            title = "Null-fields Manga",
            author = null,
            artist = null,
            description = null,
            thumbnailUrl = null,
            notes = null,
            readerBackgroundColor = null,
        )
        val json = json.encodeToString(BackupData(manga = listOf(manga)))
        val restored = this.json.decodeFromString<BackupData>(json)
        val rm = restored.manga[0]
        assertNull(rm.author)
        assertNull(rm.notes)
        assertNull(rm.readerBackgroundColor)
    }
}
