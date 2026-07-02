package app.otakureader.feature.details

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the chapterFlags encode/decode used to persist chapter list sort order and
 * read/downloaded filters per-manga (mirrors Tachiyomi/Mihon's Manga.chapterFlags bit layout).
 */
class DetailsMviTest {

    @Test
    fun `chapterFlagsOf round-trips sort order`() {
        val ascFlags = chapterFlagsOf(DetailsContract.ChapterSortOrder.ASCENDING, DetailsContract.ChapterFilter())
        val descFlags = chapterFlagsOf(DetailsContract.ChapterSortOrder.DESCENDING, DetailsContract.ChapterFilter())

        assertEquals(DetailsContract.ChapterSortOrder.ASCENDING, chapterSortOrderFromFlags(ascFlags))
        assertEquals(DetailsContract.ChapterSortOrder.DESCENDING, chapterSortOrderFromFlags(descFlags))
    }

    @Test
    fun `chapterFlagsOf round-trips read filter`() {
        for (state in DetailsContract.TriState.entries) {
            val filter = DetailsContract.ChapterFilter(read = state)
            val flags = chapterFlagsOf(DetailsContract.ChapterSortOrder.DESCENDING, filter)
            assertEquals(state, chapterFilterFromFlags(flags).read)
        }
    }

    @Test
    fun `chapterFlagsOf round-trips downloaded filter`() {
        for (state in DetailsContract.TriState.entries) {
            val filter = DetailsContract.ChapterFilter(downloaded = state)
            val flags = chapterFlagsOf(DetailsContract.ChapterSortOrder.DESCENDING, filter)
            assertEquals(state, chapterFilterFromFlags(flags).downloaded)
        }
    }

    @Test
    fun `chapterFilterFromFlags preserves the supplied scanlator and search query`() {
        val flags = chapterFlagsOf(DetailsContract.ChapterSortOrder.DESCENDING, DetailsContract.ChapterFilter())
        val filter = chapterFilterFromFlags(flags, scanlator = "Group A", chapterSearchQuery = "query")

        assertEquals("Group A", filter.scanlator)
        assertEquals("query", filter.chapterSearchQuery)
    }

    @Test
    fun `zero flags decode to defaults`() {
        assertEquals(DetailsContract.ChapterSortOrder.DESCENDING, chapterSortOrderFromFlags(0))
        assertEquals(DetailsContract.ChapterFilter(), chapterFilterFromFlags(0))
    }
}
