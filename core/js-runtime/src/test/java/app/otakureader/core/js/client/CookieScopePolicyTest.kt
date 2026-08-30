package app.otakureader.core.js.client

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which of the user's stored cookies a JavaScript source is allowed to see.
 *
 * The bridge lets a source request any public HTTPS host by design — the blocklist stops private
 * addresses, not the open internet — so once the shared client gained a cookie jar, a source could
 * ask for a site the user holds a WebView session with and read the response back as the logged-in
 * user. Tracker sign-in runs through that same WebView, so those sessions were in reach.
 *
 * The rule is registrable domain: a source sees the cookies for its own domain and nothing else.
 * These cases are the rule itself, tested apart from the request machinery, because a mistake here
 * is a credential leak rather than a broken feature.
 */
class CookieScopePolicyTest {

    private fun url(value: String) = value.toHttpUrl()

    private val source = listOf(url("https://example.org/"))

    @Test
    fun `a source's own host is owned`() {
        assertTrue(ownedBySource(url("https://example.org/manga/1"), source))
    }

    /**
     * Looser than `isOriginChange` on purpose: a source spanning `example.org` and
     * `api.example.org` is ordinary, and refusing it its own session would break the source.
     */
    @Test
    fun `a subdomain of the source is owned`() {
        assertTrue(ownedBySource(url("https://api.example.org/v1/manga"), source))
        assertTrue(ownedBySource(url("https://cdn.example.org/img.jpg"), source))
    }

    /** The case this whole change exists for. */
    @Test
    fun `an unrelated host is not owned`() {
        assertFalse(ownedBySource(url("https://myanimelist.net/profile"), source))
        assertFalse(ownedBySource(url("https://anilist.co/user"), source))
    }

    /**
     * A source whose API lives on a second domain gets both, which is why the provenance carries
     * `baseUrl` *and* `apiUrl` rather than just the base.
     */
    @Test
    fun `a second declared domain is owned`() {
        val both = listOf(url("https://example.org/"), url("https://example-api.net/"))

        assertTrue(ownedBySource(url("https://example-api.net/v1"), both))
        assertTrue(ownedBySource(url("https://example.org/x"), both))
        assertFalse(ownedBySource(url("https://myanimelist.net/"), both))
    }

    /**
     * The public suffix list is doing real work here. Matching by "last two labels" would make
     * every `*.co.uk` site one domain, so a source on `attacker.co.uk` would be handed cookies for
     * `bank.co.uk`. This is the case a hand-rolled suffix check gets wrong.
     */
    @Test
    fun `two sites under the same public suffix are not related`() {
        val ukSource = listOf(url("https://attacker.co.uk/"))

        assertFalse(ownedBySource(url("https://victim.co.uk/"), ukSource))
        assertTrue(ownedBySource(url("https://www.attacker.co.uk/"), ukSource))
    }

    /**
     * A prefix that merely *looks* related is not. `example.org.evil.test` registers under
     * `evil.test`, and a substring or `endsWith` check would hand it the source's cookies.
     */
    @Test
    fun `a lookalike host is not owned`() {
        assertFalse(ownedBySource(url("https://example.org.evil.test/"), source))
        assertFalse(ownedBySource(url("https://notexample.org/"), source))
    }

    /**
     * `topPrivateDomain()` returns null for a literal address, so the comparison falls back to an
     * exact host match — strict, which is the right default for a decision about credentials.
     */
    @Test
    fun `an IP address matches only itself`() {
        val ipSource = listOf(url("https://93.184.216.34/"))

        assertTrue(ownedBySource(url("https://93.184.216.34/x"), ipSource))
        assertFalse(ownedBySource(url("https://93.184.216.35/x"), ipSource))
        assertFalse(ownedBySource(url("https://example.org/"), ipSource))
    }

    /** Hostnames are case-insensitive; a source must not be locked out by capitalisation. */
    @Test
    fun `host comparison ignores case`() {
        assertTrue(ownedBySource(url("https://EXAMPLE.org/x"), listOf(url("https://example.ORG/"))))
    }

    /**
     * No provenance means no cookies. A request that arrives without a source cannot have its
     * owner established, and the safe answer is to send none rather than to fall back on all.
     */
    @Test
    fun `a request with no declared source owns nothing`() {
        assertFalse(ownedBySource(url("https://example.org/"), emptyList()))
    }
}
