package app.otakureader.data.tracking.di

/**
 * Tracker OAuth / API credentials.
 *
 * **Important:** Replace the placeholder values below with real credentials
 * before publishing the app. In a CI/CD pipeline these should be injected
 * via `BuildConfig` fields that are populated from encrypted secrets, e.g.:
 *
 * ```kotlin
 * // app/build.gradle.kts
 * android {
 *     defaultConfig {
 *         buildConfigField("String", "KITSU_CLIENT_ID", "\"${System.getenv("KITSU_CLIENT_ID") ?: ""}\"")
 *     }
 * }
 * ```
 *
 * And then reference them here:
 * ```kotlin
 * const val KITSU_CLIENT_ID = BuildConfig.KITSU_CLIENT_ID
 * ```
 */
object TrackerCredentials {
    // Kitsu — register at https://kitsu.app/api/edge/
    const val KITSU_CLIENT_ID = ""
    const val KITSU_CLIENT_SECRET = ""

    // MyAnimeList — register at https://myanimelist.net/apiconfig
    const val MAL_CLIENT_ID = ""
    const val MAL_CLIENT_SECRET = ""
    const val MAL_REDIRECT_URI = "app.otakureader://mal-oauth"

    // Shikimori — register at https://shikimori.one/oauth/applications
    const val SHIKIMORI_CLIENT_ID = ""
    const val SHIKIMORI_CLIENT_SECRET = ""
    const val SHIKIMORI_REDIRECT_URI = "app.otakureader://shikimori-oauth"
}
