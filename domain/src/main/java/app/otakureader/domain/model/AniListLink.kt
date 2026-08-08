package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/**
 * Which AniList media a local manga is, once something has decided.
 *
 * Distinct from [MangaMetadata], which is what AniList *said* about that media and is a cache with
 * a TTL. This is the decision itself, and it outlives any number of cache clears.
 */
@Immutable
data class AniListLink(
    val mangaId: Long,
    val anilistId: Long,
    /**
     * True only when the user picked this by hand.
     *
     * Auto-matching runs whenever a manga has no link, so without this flag the next auto-match
     * would overwrite a correction with the same wrong guess that made the correction necessary.
     */
    val userConfirmed: Boolean = false,
    /** When the link was established, epoch millis. Diagnostic only; nothing keys off it. */
    val matchedAt: Long = 0L,
)
