package app.otakureader.domain.model

/**
 * Supported external tracking services.
 */
enum class TrackService(val id: Int, val displayName: String) {
    MAL(1, "MyAnimeList"),
    ANILIST(2, "AniList"),
    KITSU(3, "Kitsu");

    companion object {
        fun fromId(id: Int): TrackService? = entries.find { it.id == id }
    }
}
