package app.otakureader.data.tracking.tracker

import app.otakureader.core.preferences.TrackerTokenStore
import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.di.TrackerCredentials
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Covers the login URL AniList is sent to.
 *
 * Before this override existed, `Tracker.authorizationUrl` returned null and `TrackingViewModel`
 * fell back to a bare `https://anilist.co/api/v2/oauth/authorize` — no `client_id`, no
 * `redirect_uri`, no `response_type`. AniList rejects that outright, so login could not complete
 * at all. The assertions below are on the *parameters*, because the endpoint alone was always
 * right; it was everything after the `?` that was missing.
 */
class AniListAuthorizationUrlTest {

    private fun tracker(): AniListTracker {
        val tokenStore = mockk<TrackerTokenStore>(relaxed = true)
        every { tokenStore.getTokens(any()) } returns null
        return AniListTracker(mockk<AniListApi>(), tokenStore)
    }

    @Test
    fun `authorization url carries every parameter AniList requires`() {
        // CI builds inject the id; a local build without the env var legitimately has none, and
        // the null-return case is covered by its own test below.
        assumeTrue(TrackerCredentials.ANILIST_CLIENT_ID.isNotBlank())

        val url = tracker().authorizationUrl("unused-verifier")!!

        assertTrue(url, url.startsWith("https://anilist.co/api/v2/oauth/authorize?"))
        assertTrue(url, url.contains("client_id=${TrackerCredentials.ANILIST_CLIENT_ID}"))
        assertTrue(url, url.contains("redirect_uri=${TrackerCredentials.ANILIST_REDIRECT_URI}"))
        // Implicit grant: `login()` takes a bearer token directly and does no code exchange.
        // `response_type=code` would hand back something this tracker cannot redeem.
        assertTrue(url, url.contains("response_type=token"))
    }

    @Test
    fun `no client id yields null rather than a url that cannot work`() {
        assumeTrue(TrackerCredentials.ANILIST_CLIENT_ID.isBlank())

        // Returning null keeps the existing "tracker not configured" path. Returning a URL
        // missing its client_id would send the user to a page that always fails, which reads
        // as a broken tracker rather than an unconfigured build.
        assertNull(tracker().authorizationUrl("unused-verifier"))
    }
}
