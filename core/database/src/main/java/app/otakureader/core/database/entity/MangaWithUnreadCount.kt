package app.otakureader.core.database.entity

import androidx.room.Embedded

/**
 * Entity representing a Manga with its unread chapter count.
 * Used for efficient library queries that need both manga data and unread counts.
 */
data class MangaWithUnreadCount(
    @Embedded val manga: MangaEntity,
    val unreadCount: Int
)
