package app.komikku.data.mapper

import app.komikku.domain.model.Manga
import app.komikku.domain.model.MangaStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappersTest {

    @Test
    fun `Manga toEntity maps all fields correctly`() {
        val manga = Manga(
            id = 1L,
            sourceId = "source_id",
            url = "url",
            title = "title",
            author = "author",
            artist = "artist",
            description = "description",
            genres = listOf("Action", "Adventure"),
            status = MangaStatus.ONGOING,
            thumbnailUrl = "thumbnail_url",
            coverLastModified = 123456789L,
            favorite = true,
            dateAdded = 987654321L,
            lastUpdate = 111111111L,
            unreadCount = 5,
            downloadedCount = 2,
            tags = listOf("Tag1", "Tag2")
        )

        val entity = manga.toEntity()

        assertEquals(manga.id, entity.id)
        assertEquals(manga.sourceId, entity.sourceId)
        assertEquals(manga.url, entity.url)
        assertEquals(manga.title, entity.title)
        assertEquals(manga.author, entity.author)
        assertEquals(manga.artist, entity.artist)
        assertEquals(manga.description, entity.description)
        assertEquals("Action|||Adventure", entity.genres)
        assertEquals(manga.status.ordinal, entity.status)
        assertEquals(manga.thumbnailUrl, entity.thumbnailUrl)
        assertEquals(manga.coverLastModified, entity.coverLastModified)
        assertEquals(manga.favorite, entity.favorite)
        assertEquals(manga.dateAdded, entity.dateAdded)
        assertEquals(manga.lastUpdate, entity.lastUpdate)
        assertEquals("Tag1|||Tag2", entity.tags)
    }
}
