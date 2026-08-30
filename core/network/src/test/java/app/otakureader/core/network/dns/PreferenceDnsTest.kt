package app.otakureader.core.network.dns

import app.otakureader.core.preferences.DohProvider
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.InetAddress

/**
 * Resolver selection (#1208). The DoH lookups themselves need the network and are not exercised
 * here; what is testable — and what would break silently — is which resolver gets chosen and how
 * often one is built.
 */
class PreferenceDnsTest {

    private var provider = DohProvider.OFF
    private val dns = PreferenceDns { provider }

    /**
     * A `PreferenceDns` reaching for the resolver of whichever provider is *currently* selected is
     * the point, so this drives the choice rather than asserting on a captured one. Reflection-free:
     * the system resolver is observable by resolving a name that needs no network.
     */
    private fun resolveLoopback(): List<InetAddress> = dns.lookup("localhost")

    @Test
    fun `off resolves through the system resolver`() {
        provider = DohProvider.OFF

        assertEquals(Dns.SYSTEM.lookup("localhost"), resolveLoopback())
    }

    /**
     * A [okhttp3.dnsoverhttps.DnsOverHttps] holds a connection pool, so building one per lookup
     * would throw away every kept-alive connection to the resolver and turn each name into a fresh
     * TLS handshake.
     */
    @Test
    fun `a provider's resolver is built once and reused`() {
        provider = DohProvider.CLOUDFLARE

        assertSame(dns.resolverForTesting(), dns.resolverForTesting())
    }

    /** Distinct providers must not share a resolver, or switching would appear to do nothing. */
    @Test
    fun `each provider gets its own resolver`() {
        provider = DohProvider.CLOUDFLARE
        val cloudflare = dns.resolverForTesting()
        provider = DohProvider.QUAD9

        assertNotSame(cloudflare, dns.resolverForTesting())
    }

    /** Off is the system resolver itself, not a wrapper — nothing to build, nothing to cache. */
    @Test
    fun `off is the system resolver itself`() {
        provider = DohProvider.OFF

        assertSame(Dns.SYSTEM, dns.resolverForTesting())
    }
}
