package app.otakureader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Which AniList media a manga is, once something has decided.
 *
 * ### Why this is not a column on [MangaMetadataEntity]
 *
 * That row already carries an `anilistId`, so reusing it would need no migration at all — and it
 * would be wrong, because the two have different lifetimes. `manga_metadata` is a **cache**: it has
 * a seven-day TTL and is rewritten outright by every refresh, so nothing written there
 * survives on the user's terms.
 * The link is **durable state**, and when [userConfirmed] is set it is a decision the user made by
 * hand. Storing a correction in a row designed to be discarded means the correction is discarded
 * with it, which defeats the picker that produced it.
 *
 * ### Why not a column on [MangaEntity]
 *
 * `MangaEntity` is already 38 columns wide and is the library's core record, read by nearly every
 * query in the app. A third party's identifier does not belong in it, and every `SELECT *` would
 * carry two more columns to serve one screen.
 *
 * ### [userConfirmed] is the whole point
 *
 * Auto-matching runs whenever a manga has no link. Without a flag saying "a human chose this", the
 * next auto-match would happily overwrite a correction with the same wrong guess that made the
 * correction necessary. Anything written by the matcher has it false; only the picker sets it true.
 *
 * `ON DELETE CASCADE` because a link to a manga that no longer exists is meaningless, and — as with
 * `manga_metadata` — the cascade is the entire reclamation story. One row per manga, deleted with
 * the manga, so the table is bounded by the library rather than by how long the app has been used.
 */
@Entity(
    tableName = "manga_anilist_link",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MangaAniListLinkEntity(
    @PrimaryKey val mangaId: Long,
    val anilistId: Long,
    /** True only when the user picked this in the wrong-match picker. Never set by auto-matching. */
    val userConfirmed: Boolean = false,
    /** When the link was established, epoch millis. Diagnostic only; nothing keys off it. */
    val matchedAt: Long = 0L,
)
