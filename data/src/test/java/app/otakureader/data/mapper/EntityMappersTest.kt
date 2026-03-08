package app.otakureader.data.mapper

import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappersTest {

    @Test
    fun chapterEntity_toChapter_mapsCorrectly() {
        // Arrange
        val entity = ChapterEntity(
            id = 1L,
            mangaId = 2L,
            url = "/chapter/1",
            name = "Chapter 1",
            scanlator = "Scan Group",
            dateUpload = 123456789L,
            chapterNumber = 1.0f,
            read = true,
            bookmark = true,
            lastPageRead = 5
        )

        // Act
        val domainModel = entity.toChapter()

        // Assert
        assertEquals(entity.id, domainModel.id)
        assertEquals(entity.mangaId, domainModel.mangaId)
        assertEquals(entity.url, domainModel.url)
        assertEquals(entity.name, domainModel.name)
        assertEquals(entity.scanlator, domainModel.scanlator)
        assertEquals(entity.dateUpload, domainModel.dateUpload)
        assertEquals(entity.chapterNumber, domainModel.chapterNumber)
        assertEquals(entity.read, domainModel.read)
        assertEquals(entity.bookmark, domainModel.bookmark)
        assertEquals(entity.lastPageRead, domainModel.lastPageRead)
    }

    @Test
    fun chapter_toEntity_mapsCorrectly() {
        // Arrange
        val domainModel = Chapter(
            id = 1L,
            mangaId = 2L,
            url = "/chapter/1",
            name = "Chapter 1",
            scanlator = "Scan Group",
            dateUpload = 123456789L,
            chapterNumber = 1.0f,
            read = true,
            bookmark = true,
            lastPageRead = 5
        )

        // Act
        val entity = domainModel.toEntity()

        // Assert
        assertEquals(domainModel.id, entity.id)
        assertEquals(domainModel.mangaId, entity.mangaId)
        assertEquals(domainModel.url, entity.url)
        assertEquals(domainModel.name, entity.name)
        assertEquals(domainModel.scanlator, entity.scanlator)
        assertEquals(domainModel.dateUpload, entity.dateUpload)
        assertEquals(domainModel.chapterNumber, entity.chapterNumber)
        assertEquals(domainModel.read, entity.read)
        assertEquals(domainModel.bookmark, entity.bookmark)
        assertEquals(domainModel.lastPageRead, entity.lastPageRead)
    }

    @Test
    fun mangaEntity_toManga_mapsCorrectly() {
        // Arrange
        val entity = MangaEntity(
            id = 10L,
            sourceId = 1L,
            url = "/manga/one-piece",
            title = "One Piece",
            author = "Eiichiro Oda",
            artist = "Eiichiro Oda",
            description = "A pirate adventure.",
            genre = "Action|||Adventure|||Fantasy",
            status = MangaStatus.ONGOING.ordinal,
            thumbnailUrl = "https://example.com/cover.jpg",
            favorite = true,
            initialized = true
        )

        // Act
        val manga = entity.toManga()

        // Assert
        assertEquals(entity.id, manga.id)
        assertEquals(entity.sourceId, manga.sourceId)
        assertEquals(entity.url, manga.url)
        assertEquals(entity.title, manga.title)
        assertEquals(entity.author, manga.author)
        assertEquals(entity.artist, manga.artist)
        assertEquals(entity.description, manga.description)
        assertEquals(listOf("Action", "Adventure", "Fantasy"), manga.genre)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals(entity.thumbnailUrl, manga.thumbnailUrl)
        assertEquals(entity.favorite, manga.favorite)
        assertEquals(entity.initialized, manga.initialized)
    }

    @Test
    fun mangaEntity_toManga_withNullGenre_returnsEmptyList() {
        val entity = MangaEntity(
            id = 1L,
            sourceId = 1L,
            url = "/manga/test",
            title = "Test",
            genre = null
        )

        val manga = entity.toManga()

        assertEquals(emptyList<String>(), manga.genre)
    }

    @Test
    fun manga_toEntity_mapsCorrectly() {
        // Arrange
        val manga = Manga(
            id = 10L,
            sourceId = 1L,
            url = "/manga/one-piece",
            title = "One Piece",
            author = "Eiichiro Oda",
            artist = "Eiichiro Oda",
            description = "A pirate adventure.",
            genre = listOf("Action", "Adventure", "Fantasy"),
            status = MangaStatus.ONGOING,
            thumbnailUrl = "https://example.com/cover.jpg",
            favorite = true,
            initialized = true
        )

        // Act
        val entity = manga.toEntity()

        // Assert
        assertEquals(manga.id, entity.id)
        assertEquals(manga.sourceId, entity.sourceId)
        assertEquals(manga.url, entity.url)
        assertEquals(manga.title, entity.title)
        assertEquals(manga.author, entity.author)
        assertEquals(manga.artist, entity.artist)
        assertEquals(manga.description, entity.description)
        assertEquals("Action|||Adventure|||Fantasy", entity.genre)
        assertEquals(MangaStatus.ONGOING.ordinal, entity.status)
        assertEquals(manga.thumbnailUrl, entity.thumbnailUrl)
        assertEquals(manga.favorite, entity.favorite)
        assertEquals(manga.initialized, entity.initialized)
    }

    @Test
    fun chapterEntity_toChapter_withNullScanlator_mapsCorrectly() {
        val entity = ChapterEntity(
            id = 5L,
            mangaId = 1L,
            url = "/chapter/5",
            name = "Chapter 5",
            scanlator = null,
            chapterNumber = 5.0f
        )

        val chapter = entity.toChapter()

        assertEquals(null, chapter.scanlator)
        assertEquals(5.0f, chapter.chapterNumber)
    }

    @Test
    fun chapter_roundtrip_preservesAllFields() {
        val original = Chapter(
            id = 99L,
            mangaId = 1L,
            url = "/chapter/99",
            name = "Chapter 99",
            scanlator = "Scan Team",
            read = true,
            bookmark = false,
            lastPageRead = 12,
            chapterNumber = 99.5f,
            dateUpload = 9999999L
        )

        val roundTripped = original.toEntity().toChapter()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.mangaId, roundTripped.mangaId)
        assertEquals(original.url, roundTripped.url)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.scanlator, roundTripped.scanlator)
        assertEquals(original.read, roundTripped.read)
        assertEquals(original.bookmark, roundTripped.bookmark)
        assertEquals(original.lastPageRead, roundTripped.lastPageRead)
        assertEquals(original.chapterNumber, roundTripped.chapterNumber)
        assertEquals(original.dateUpload, roundTripped.dateUpload)
    }
}
