package app.otakureader.data.tracking.tracker

import app.otakureader.core.preferences.TrackerTokenStore
import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.di.TrackerCredentials
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Covers the login URL AniList is sent to.
 *
 * Before this override existed, `Tracker.authorizationUrl` returned null and `TrackingViewModel`
 * fell back to a bare `https://anilist.co/api/v2/oauth/authorize` — no `client_id`, no
 * `redirect_uri`, no `response_type`. AniList rejects that outright, so login could not complete
 * at all. The assertions below are on the *parameters*, because the endpoint alone was always
 * right; it was everything after the `?` that was missing.
 *
 * Every test passes the client id in through the constructor. Two earlier versions of this file
 * read the build-injected `ANILIST_CLIENT_ID` instead: the first gated each test on it with
 * `assumeTrue`, so exactly one branch could run and the skipped one reported as a pass; the second
 * compared the tracker against a helper fed the same constant, which in an unconfigured build made
 * both sides null and the assertion true no matter what the tracker did. Supplying the id removes
 * the ambient build state from the question in both directions.
 */
class AniListAuthorizationUrlTest {

    private fun tracker(clientId: String): AniListTracker {
        val tokenStore = mockk<TrackerTokenStore>(relaxed = true)
        every { tokenStore.getTokens(any()) } returns null
        return AniListTracker(mockk<AniListApi>(), tokenStore, clientId)
    }

    @Test
    fun `authorization url carries every parameter AniList requires`() {
        val url = tracker("test-client-id").authorizationUrl("unused-verifier", "state-token")!!

        assertTrue(url, url.startsWith("https://anilist.co/api/v2/oauth/authorize?"))
        assertTrue(url, url.contains("client_id=test-client-id"))
        assertTrue(url, url.contains("redirect_uri=${TrackerCredentials.ANILIST_REDIRECT_URI}"))
        // Implicit grant: `login()` takes a bearer token directly and does no code exchange.
        // `response_type=code` would hand back something this tracker cannot redeem.
        assertTrue(url, url.contains("response_type=token"))
        // Without this the provider echoes nothing back, and the callback's CSRF comparison has
        // no value to compare against. The implicit grant delivers the token straight to the
        // redirect, so this is the only thing binding the token that arrives to the login the app
        // started.
        assertTrue(url, url.contains("state=state-token"))
    }

    @Test
    fun `a state needing encoding survives the round trip`() {
        // A UUID needs no encoding, so this is about not breaking silently if the generator ever
        // changes: an unencoded `&` would split the state into a second parameter, and the
        // callback's equality check against the stored value would then fail on every login.
        val url = tracker("test-client-id").authorizationUrl("unused-verifier", "a&b=c")!!

        assertTrue(url, url.contains("state=a%26b%3Dc"))
        assertEquals("a&b=c", URLDecoder.decode("a%26b%3Dc", StandardCharsets.UTF_8.name()))
    }

    @Test
    fun `no client id yields null rather than a url that cannot work`() {
        // Null is the interface's "this tracker has no authorization URL", and
        // TrackingViewModel now reports the tracker as unavailable instead of substituting a
        // bare endpoint. Returning a URL missing its client_id would send the user to a page
        // that always fails, which reads as a broken tracker rather than an unconfigured build.
        assertNull(tracker("").authorizationUrl("unused-verifier", "state-token"))
        // Whitespace-only is the same situation: an env var set to nothing useful.
        assertNull(tracker("   ").authorizationUrl("unused-verifier", "state-token"))
    }
}
