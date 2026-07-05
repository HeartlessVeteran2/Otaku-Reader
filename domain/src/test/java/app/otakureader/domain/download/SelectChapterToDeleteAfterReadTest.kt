package app.otakureader.domain.download

import app.otakureader.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectChapterToDeleteAfterReadTest {

    private fun makeChapter(id: Long, number: Float) = Chapter(
        id = id,
        mangaId = 1L,
        url = "url/$id",
        name = "Chapter $number",
        chapterNumber = number,
    )

    // Chapters deliberately out of order to prove the function sorts by chapterNumber.
    private val chapters = listOf(
        makeChapter(id = 30L, number = 3f),
        makeChapter(id = 10L, number = 1f),
        makeChapter(id = 50L, number = 5f),
        makeChapter(id = 20L, number = 2f),
        makeChapter(id = 40L, number = 4f),
    )

    @Test
    fun `slots zero returns the just-read chapter itself`() {
        val target = selectChapterToDeleteAfterRead(chapters, justReadChapterId = 30L, slots = 0)
        assertEquals(30L, target?.id)
    }

    @Test
    fun `slots one returns the previous chapter in reading order`() {
        // Just read chapter 3 with keep-last-1 → chapter 2 gets deleted
        val target = selectChapterToDeleteAfterRead(chapters, justReadChapterId = 30L, slots = 1)
        assertEquals(20L, target?.id)
    }

    @Test
    fun `slots two skips back two chapters`() {
        // Just read chapter 5 with keep-last-2 → chapter 3 gets deleted
        val target = selectChapterToDeleteAfterRead(chapters, justReadChapterId = 50L, slots = 2)
        assertEquals(30L, target?.id)
    }

    @Test
    fun `returns null when not enough earlier chapters exist`() {
        // Just read chapter 2 with keep-last-3 → nothing far enough back to delete
        assertNull(selectChapterToDeleteAfterRead(chapters, justReadChapterId = 20L, slots = 3))
    }

    @Test
    fun `returns null when just-read chapter is first in reading order`() {
        assertNull(selectChapterToDeleteAfterRead(chapters, justReadChapterId = 10L, slots = 1))
    }

    @Test
    fun `returns null when just-read chapter is not in the list`() {
        assertNull(selectChapterToDeleteAfterRead(chapters, justReadChapterId = 999L, slots = 1))
    }

    @Test
    fun `slots zero returns null when just-read chapter is not in the list`() {
        assertNull(selectChapterToDeleteAfterRead(chapters, justReadChapterId = 999L, slots = 0))
    }

    @Test
    fun `empty chapter list returns null`() {
        assertNull(selectChapterToDeleteAfterRead(emptyList(), justReadChapterId = 10L, slots = 1))
    }
}
