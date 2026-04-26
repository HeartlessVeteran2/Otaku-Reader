package app.otakureader.core.network

import okhttp3.CertificatePinner

/**
 * Certificate pins for tracker OAuth/API endpoints.
 *
 * Pins are SHA-256 SPKI hashes of the leaf or intermediate certificates.
 * Update these whenever a tracker rotates its certificate chain.
 *
 * How to get new pins:
 *   openssl s_client -connect <host>:443 -showcerts </dev/null 2>/dev/null \
 *     | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary | base64
 *
 * Or use: `okhttp3.CertificatePinner.pin(certificate)` in a test.
 *
 * All domains are pinned to both the current leaf and at least one CA backup pin
 * so that a leaf rotation does not break the app until the next release.
 */
object TrackerCertificatePinner {

    /**
     * Builds a [CertificatePinner] covering all tracker OAuth endpoints.
     * Add this to the tracker-specific [okhttp3.OkHttpClient].
     */
    fun build(): CertificatePinner = CertificatePinner.Builder()
        // ── MyAnimeList ─────────────────────────────────────────────────────
        // OAuth: myanimelist.net/v1/oauth2/  API: api.myanimelist.net/v2/
        // DigiCert Global Root CA (backup)
        .add("myanimelist.net", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
        .add("myanimelist.net", "sha256/WoiWRyIOVNa9ihaBciRSC7XHjliYS9VwUGOIud4PB18=")
        .add("api.myanimelist.net", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
        .add("api.myanimelist.net", "sha256/WoiWRyIOVNa9ihaBciRSC7XHjliYS9VwUGOIud4PB18=")
        // ── AniList ─────────────────────────────────────────────────────────
        // graphql.anilist.co (Cloudflare CDN)
        // Cloudflare Inc ECC CA-3 + ISRG Root X1 backup
        .add("graphql.anilist.co", "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMv2M01eUNwHcm0etyk=")
        .add("graphql.anilist.co", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
        // ── Kitsu ───────────────────────────────────────────────────────────
        // kitsu.app (Let's Encrypt / DigiCert)
        .add("kitsu.app", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
        .add("kitsu.app", "sha256/Vjs8r4z+80wjNcr1YKepWQboSIRi63WsWXhIMN+eWys=")
        // ── MangaUpdates ────────────────────────────────────────────────────
        // api.mangaupdates.com
        .add("api.mangaupdates.com", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
        .add("api.mangaupdates.com", "sha256/Vjs8r4z+80wjNcr1YKepWQboSIRi63WsWXhIMN+eWys=")
        // ── Shikimori ───────────────────────────────────────────────────────
        // shikimori.one
        .add("shikimori.one", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
        .add("shikimori.one", "sha256/Vjs8r4z+80wjNcr1YKepWQboSIRi63WsWXhIMN+eWys=")
        .build()
}
