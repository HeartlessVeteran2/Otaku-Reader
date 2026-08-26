package app.otakureader.core.network

import okhttp3.CertificatePinner

/**
 * Certificate pins for tracker OAuth/API endpoints.
 *
 * ## What is pinned, and why it is not the leaf
 *
 * Each host pins its **issuing intermediate CA**, plus the **root** where the server sends one.
 * Leaf certificates are deliberately not pinned any more. That is a correction, not a preference:
 * pinning leaves broke every tracker for every installed user, twice over.
 *
 * The leaf pins recorded here on 2026-05-04 had all six rotated by 2026-08-01, which is what
 * opened #1218. Re-measuring on 2026-08-26 showed `kitsu.app` had rotated *again* in the 26 days
 * since. These hosts sit behind Let's Encrypt and Google Trust Services, which issue 90-day
 * certificates and rotate well inside that; this app does not ship a release every six weeks. A
 * leaf pin is therefore a scheduled outage, and OkHttp fails closed — when no pin matches, every
 * tracker call throws and sync dies silently.
 *
 * Intermediates change on a timescale of years, so pinning them is the level that both survives
 * between releases and still does the job pinning is for: it rejects a certificate minted by any
 * *other* CA, which is the realistic interception threat. Combined with normal hostname
 * verification, that is the standard trade for an app on an infrequent release cadence.
 *
 * ## The old backup pin was never protecting anything
 *
 * This file used to carry one shared "DigiCert Global Root G3" pin
 * (`IgG8q1Egd9jBnrvbTB6BsLEvZ1aYqrym+IPQIxy5qiE=`) on every host, described as the safety net that
 * would keep the app working across a leaf rotation. Measured against the live chains, that key
 * appears in **none** of them. The six hosts use three different CAs — DigiCert for MyAnimeList,
 * Google Trust Services for AniList and Kitsu, Let's Encrypt/ISRG for MangaUpdates and Shikimori —
 * so one shared root could not have covered them even in principle. When the leaves rotated there
 * was nothing behind them, and that is why the breakage was total rather than partial.
 *
 * ## Updating pins
 *
 * Do not compute these from a sandboxed or proxied environment: an intercepting proxy presents its
 * own certificate, so what you measure is the proxy's key, and pinning it breaks TLS for every
 * user while looking entirely normal in review. The `cert-pin-check` workflow runs from a direct
 * connection and prints the full served chain for every host — take the values from its run log.
 * Trigger it with `workflow_dispatch`; it also runs monthly and now fails the job (rather than
 * only opening an issue) when no pinned key appears in a host's chain.
 *
 * Verified 2026-08-26 against the served chains. Issue tracking: #994, #1218.
 */
object TrackerCertificatePinner {

    // ── Shared issuers ───────────────────────────────────────────────────────
    // AniList and Kitsu share one chain, as do MangaUpdates and Shikimori. Named once so a future
    // rotation is a single edit and the two hosts cannot silently drift apart.

    /** Google Trust Services WE1 — issuing intermediate for AniList and Kitsu. */
    private const val GTS_WE1 = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="

    /** GTS Root R4 — root behind [GTS_WE1]. */
    private const val GTS_ROOT_R4 = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

    /** Let's Encrypt YR1 — issuing intermediate for MangaUpdates and Shikimori. */
    private const val LETSENCRYPT_YR1 = "sha256/LoMHBotttiDko50Gi13uXW71eIy7LAttI+rYT8wXF4w="

    /** ISRG Root YR — root behind [LETSENCRYPT_YR1]. */
    private const val ISRG_ROOT_YR = "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88="

    /** DigiCert Global G3 TLS ECC SHA384 2020 CA1 — issuing intermediate for MyAnimeList. */
    private const val DIGICERT_G3_TLS_ECC = "sha256/qBRjZmOmkSNJL0p70zek7odSIzqs/muR4Jk9xYyCP+E="

    /**
     * Builds a [CertificatePinner] covering all tracker OAuth endpoints.
     * Add this to the tracker-specific [okhttp3.OkHttpClient].
     */
    fun build(): CertificatePinner = CertificatePinner.Builder()
        // ── MyAnimeList ──────────────────────────────────────────────────────
        // OAuth: myanimelist.net/v1/oauth2/  API: api.myanimelist.net/v2/
        // Both are served by one *.myanimelist.net wildcard certificate. Only the intermediate is
        // pinned: this chain does not send a root, so there is no second key to fall back to.
        .add("myanimelist.net", DIGICERT_G3_TLS_ECC)
        .add("api.myanimelist.net", DIGICERT_G3_TLS_ECC)
        // ── AniList ──────────────────────────────────────────────────────────
        .add("graphql.anilist.co", GTS_WE1)
        .add("graphql.anilist.co", GTS_ROOT_R4)
        // ── Kitsu ────────────────────────────────────────────────────────────
        .add("kitsu.app", GTS_WE1)
        .add("kitsu.app", GTS_ROOT_R4)
        // ── MangaUpdates ─────────────────────────────────────────────────────
        .add("api.mangaupdates.com", LETSENCRYPT_YR1)
        .add("api.mangaupdates.com", ISRG_ROOT_YR)
        // ── Shikimori ────────────────────────────────────────────────────────
        .add("shikimori.one", LETSENCRYPT_YR1)
        .add("shikimori.one", ISRG_ROOT_YR)
        .build()
}
