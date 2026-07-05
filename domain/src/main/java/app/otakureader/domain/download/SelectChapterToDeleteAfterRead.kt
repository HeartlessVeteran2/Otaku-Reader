package app.otakureader.domain.download

import app.otakureader.domain.model.Chapter

/**
 * Picks which chapter's download should be deleted after the chapter with [justReadChapterId]
 * has been read, honouring the "keep last N read chapters" preference.
 *
 * - [slots] = 0 → the just-read chapter itself (immediate delete-after-read).
 * - [slots] = N → the chapter N positions *earlier* in reading order (ascending
 *   [Chapter.chapterNumber]), so the N most recently read chapters stay downloaded.
 *
 * Returns null when the just-read chapter isn't in [chapters] or there is no chapter that far
 * back — in both cases nothing should be deleted.
 */
fun selectChapterToDeleteAfterRead(
    chapters: List<Chapter>,
    justReadChapterId: Long,
    slots: Int,
): Chapter? {
    if (slots <= 0) return chapters.firstOrNull { it.id == justReadChapterId }
    val readingOrder = chapters.sortedBy { it.chapterNumber }
    val justReadIndex = readingOrder.indexOfFirst { it.id == justReadChapterId }
    if (justReadIndex == -1) return null
    return readingOrder.getOrNull(justReadIndex - slots)
}
