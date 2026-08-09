package app.otakureader.domain.model

/**
 * A suggested manga, from either the local genre channel or AniList's recommendation edges.
 *
 * Exactly one of [mangaId] and [anilistId] is guaranteed present, and which one decides what
 * tapping the card can do: a local row can be opened, an AniList-only suggestion has no local
 * record to open and runs a global search by title instead — the same recourse `MangaRelationCarousel`
 * settled on for related manga in #1250.
 *
 * [matchedTags] is why the title is being suggested. Presenting a recommendation without one makes
 * it an oracle the user cannot argue with; naming the overlap lets them judge it.
 */
data class Recommendation(
    val mangaId: Long? = null,
    val anilistId: Long? = null,
    val title: String,
    val thumbnailUrl: String?,
    val sourceId: Long? = null,
    val score: Float,
    val matchedTags: List<String> = emptyList(),
)
