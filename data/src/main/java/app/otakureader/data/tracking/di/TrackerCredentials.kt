package app.otakureader.data.tracking.di

/**
 * Tracker OAuth / API credentials.
 *
 * ## Security Notice (Audit C-5)
 *
 * These constants are intentionally **empty**. Real credentials must **never** be
 * committed to source control. Compiled `const val` strings are trivially
 * extractable from APK files with tools like `apktool` or `jadx`, so pasting
 * real secrets here is equivalent to publishing them publicly.
 *
 * ### Recommended setup
 *
 * 1. Store each secret as an **encrypted CI/CD environment variable** (GitHub
 *    Actions → Settings → Secrets, or equivalent).
 * 2. Inject them into `BuildConfig` at build time in `app/build.gradle.kts`:
 *
 * ```kotlin
 * android {
 *     defaultConfig {
 *         buildConfigField("String", "KITSU_CLIENT_ID",
 *             "\"${System.getenv("KITSU_CLIENT_ID") ?: ""}\"")
 *         buildConfigField("String", "KITSU_CLIENT_SECRET",
 *             "\"${System.getenv("KITSU_CLIENT_SECRET") ?: ""}\"")
 *         buildConfigField("String", "MAL_CLIENT_ID",
 *             "\"${System.getenv("MAL_CLIENT_ID") ?: ""}\"")
 *         buildConfigField("String", "MAL_CLIENT_SECRET",
 *             "\"${System.getenv("MAL_CLIENT_SECRET") ?: ""}\"")
 *         buildConfigField("String", "SHIKIMORI_CLIENT_ID",
 *             "\"${System.getenv("SHIKIMORI_CLIENT_ID") ?: ""}\"")
 *         buildConfigField("String", "SHIKIMORI_CLIENT_SECRET",
 *             "\"${System.getenv("SHIKIMORI_CLIENT_SECRET") ?: ""}\"")
 *     }
 * }
 * ```
 *
 * 3. Replace the empty strings below with `BuildConfig.KITSU_CLIENT_ID` etc.
 * 4. Add `local.properties` (already git-ignored) for local development overrides.
 *
 * TODO(security/C-5): Wire up BuildConfig fields and remove the empty string literals.
 */
object TrackerCredentials {
    // Kitsu — register at https://kitsu.app/api/edge/
    // TODO(security/C-5): Replace with BuildConfig.KITSU_CLIENT_ID
    const val KITSU_CLIENT_ID = ""
    // TODO(security/C-5): Replace with BuildConfig.KITSU_CLIENT_SECRET
    const val KITSU_CLIENT_SECRET = ""

    // MyAnimeList — register at https://myanimelist.net/apiconfig
    // TODO(security/C-5): Replace with BuildConfig.MAL_CLIENT_ID
    const val MAL_CLIENT_ID = ""
    // TODO(security/C-5): Replace with BuildConfig.MAL_CLIENT_SECRET
    const val MAL_CLIENT_SECRET = ""
    const val MAL_REDIRECT_URI = "app.otakureader://mal-oauth"

    // Shikimori — register at https://shikimori.one/oauth/applications
    // TODO(security/C-5): Replace with BuildConfig.SHIKIMORI_CLIENT_ID
    const val SHIKIMORI_CLIENT_ID = ""
    // TODO(security/C-5): Replace with BuildConfig.SHIKIMORI_CLIENT_SECRET
    const val SHIKIMORI_CLIENT_SECRET = ""
    const val SHIKIMORI_REDIRECT_URI = "app.otakureader://shikimori-oauth"
}
