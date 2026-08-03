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
 * Both tests call [buildAniListAuthorizationUrl] with an explicit client id rather than going
 * through the tracker. The first version of this file gated each test on whether the build had
 * `ANILIST_CLIENT_ID` injected, so only one of the two could ever run and the other was skipped —
 * and a skipped test reports green. Passing the id in means both branches run in every build.
 */
class AniListAuthorizationUrlTest {

    @Test
    fun `authorization url carries every parameter AniList requires`() {
        val url = buildAniListAuthorizationUrl("test-client-id", "state-token")!!

        assertTrue(url, url.startsWith("https://anilist.co/api/v2/oauth/authorize?"))
        assertTrue(url, url.contains("client_id=test-client-id"))
        assertTrue(url, url.contains("redirect_uri=${TrackerCredentials.ANILIST_REDIRECT_URI}"))
        // Implicit grant: `login()` takes a bearer token directly and does no code exchange.
        // `response_type=code` would hand back something this tracker cannot redeem.
        assertTrue(url, url.contains("response_type=token"))
        // Without this the provider echoes nothing back, and the callback's CSRF comparison has
        // no value to compare against — which the callback used to treat as "provider omitted it,
        // carry on". The implicit grant delivers the token straight to the redirect, so this is
        // the only thing binding the token that arrives to the login this app started.
        assertTrue(url, url.contains("state=state-token"))
    }

    @Test
    fun `a state needing encoding survives the round trip`() {
        // A UUID needs no encoding, so this is about not breaking silently if the generator ever
        // changes: an unencoded `&` would split the state into a second parameter, and the
        // callback's equality check against the stored value would then fail on every login.
        val url = buildAniListAuthorizationUrl("test-client-id", "a&b=c")!!

        assertTrue(url, url.contains("state=a%26b%3Dc"))
        assertEquals("a&b=c", URLDecoder.decode("a%26b%3Dc", StandardCharsets.UTF_8.name()))
    }

    /**
     * The two tests above prove the builder is right; this one proves the tracker uses it.
     *
     * Without it, extracting the builder would have moved all the coverage off the code path the
     * app actually calls — [AniListTracker.authorizationUrl] could go back to returning null, or
     * to some other client id, and the builder tests would stay green. Asserting equality against
     * the builder rather than a literal keeps this independent of whether the build has an id
     * injected, so it runs in every build instead of being skipped in most of them.
     */
    @Test
    fun `the tracker returns the built url rather than the base null`() {
        val tokenStore = mockk<TrackerTokenStore>(relaxed = true)
        every { tokenStore.getTokens(any()) } returns null
        val tracker = AniListTracker(mockk<AniListApi>(), tokenStore)

        assertEquals(
            buildAniListAuthorizationUrl(TrackerCredentials.ANILIST_CLIENT_ID, "state-token"),
            tracker.authorizationUrl("unused-verifier", "state-token"),
        )
    }

    @Test
    fun `no client id yields null rather than a url that cannot work`() {
        // Returning null keeps the existing "tracker not configured" path. Returning a URL
        // missing its client_id would send the user to a page that always fails, which reads
        // as a broken tracker rather than an unconfigured build.
        assertNull(buildAniListAuthorizationUrl("", "state-token"))
        // Whitespace-only is the same situation: an env var set to nothing useful.
        assertNull(buildAniListAuthorizationUrl("   ", "state-token"))
    }
}
