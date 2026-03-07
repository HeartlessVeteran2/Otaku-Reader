package app.komikku.data.mapper

import app.komikku.core.database.entity.ChapterEntity
import app.komikku.domain.model.Chapter
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
            sourceOrder = 1,
            read = true,
            bookmark = true,
            lastPageRead = 5,
            totalPageCount = 20,
            dateFetch = 987654321L
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
        assertEquals(entity.sourceOrder, domainModel.sourceOrder)
        assertEquals(entity.read, domainModel.read)
        assertEquals(entity.bookmark, domainModel.bookmark)
        assertEquals(entity.lastPageRead, domainModel.lastPageRead)
        assertEquals(entity.totalPageCount, domainModel.totalPageCount)
        assertEquals(entity.dateFetch, domainModel.dateFetch)
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
            sourceOrder = 1,
            read = true,
            bookmark = true,
            lastPageRead = 5,
            totalPageCount = 20,
            dateFetch = 987654321L
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
        assertEquals(domainModel.sourceOrder, entity.sourceOrder)
        assertEquals(domainModel.read, entity.read)
        assertEquals(domainModel.bookmark, entity.bookmark)
        assertEquals(domainModel.lastPageRead, entity.lastPageRead)
        assertEquals(domainModel.totalPageCount, entity.totalPageCount)
        assertEquals(domainModel.dateFetch, entity.dateFetch)
    }
}
