package app.otakureader.data.tracking.di

import app.otakureader.data.BuildConfig

/**
 * Tracker OAuth / API credentials.
 *
 * ## Security Notice (Audit C-5 — Fixed)
 *
 * Real credentials are never committed to source control.  They are injected at
 * build time from **CI/CD encrypted environment variables** via the `buildConfigField`
 * declarations in `data/build.gradle.kts`.
 *
 * ### How to provide credentials
 *
 * **CI/CD (GitHub Actions):**
 * Add the following repository secrets in *Settings → Secrets → Actions*:
 * `KITSU_CLIENT_ID`, `KITSU_CLIENT_SECRET`,
 * `MAL_CLIENT_ID`, `MAL_CLIENT_SECRET`,
 * `SHIKIMORI_CLIENT_ID`, `SHIKIMORI_CLIENT_SECRET`.
 * The build script reads them via `System.getenv(...)`.
 *
 * **Local development:**
 * Export the variables in your shell before invoking Gradle, or add them to a
 * `local.properties` file (already git-ignored) and load them in your IDE's
 * run configuration.  If the variables are not set, the credentials default to
 * empty strings and the associated tracker will fail to authenticate — which is
 * safe for development builds that do not need those trackers.
 */
object TrackerCredentials {
    // Kitsu — register at https://kitsu.app/api/edge/
    // No client secret required: Kitsu Authorization Code + PKCE flow for public clients.
    val KITSU_CLIENT_ID: String get() = runCatching { BuildConfig.KITSU_CLIENT_ID }.getOrDefault("")
    const val KITSU_REDIRECT_URI = "app.otakureader://kitsu-oauth"

    // MyAnimeList — register at https://myanimelist.net/apiconfig
    // No client secret required: MAL PKCE flow for public mobile clients.
    val MAL_CLIENT_ID: String get() = runCatching { BuildConfig.MAL_CLIENT_ID }.getOrDefault("")
    const val MAL_REDIRECT_URI = "app.otakureader://mal-oauth"

    // Shikimori — register at https://shikimori.one/oauth/applications
    val SHIKIMORI_CLIENT_ID: String     get() = runCatching { BuildConfig.SHIKIMORI_CLIENT_ID }.getOrDefault("")
    val SHIKIMORI_CLIENT_SECRET: String get() = runCatching { BuildConfig.SHIKIMORI_CLIENT_SECRET }.getOrDefault("")
    const val SHIKIMORI_REDIRECT_URI = "app.otakureader://shikimori-oauth"
}
