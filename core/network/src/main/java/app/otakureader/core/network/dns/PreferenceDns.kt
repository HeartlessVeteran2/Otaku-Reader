package app.otakureader.core.network.dns

import app.otakureader.core.preferences.DohProvider
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves hostnames through the DNS-over-HTTPS provider the user picked, or the system resolver.
 *
 * DoH is worth having for the same reason a manga reader needs a WebView: a good share of sources
 * are unreachable not because they are down but because a resolver in the path refuses to answer
 * for them. It also stops the plaintext hostname of every source from being visible on the network.
 *
 * ## Reading the provider per lookup
 *
 * The choice is read on each call rather than captured at construction, so changing it in Settings
 * takes effect on the next request instead of the next launch — which is why the dependency is a
 * function rather than a value. Resolvers
 * are built once per provider and cached — a [DnsOverHttps] holds a connection pool, so rebuilding
 * one per lookup would throw away every kept-alive connection to the resolver.
 *
 * ## The bootstrap client
 *
 * A DoH resolver needs an HTTP client to ask its question, and that client needs a resolver. Giving
 * it the app's shared client would close the loop: resolving `cloudflare-dns.com` would require
 * resolving `cloudflare-dns.com`. So the bootstrap is a bare [OkHttpClient] on the system resolver,
 * used only to reach the provider's own endpoint.
 *
 * ## Failure is not silent, and not fatal to the app
 *
 * If the provider itself cannot be reached, the lookup fails as an ordinary [UnknownHostException]
 * and the request fails with it. Falling back to the system resolver would defeat the point for
 * anyone who chose DoH precisely because the system resolver is the problem — and it would do so
 * invisibly, which is the worst version of it.
 */
class PreferenceDns(
    private val provider: () -> DohProvider,
) : Dns {

    private val resolvers = ConcurrentHashMap<DohProvider, Dns>()

    /**
     * Built lazily, and only if some provider is ever selected — the default is [DohProvider.OFF],
     * so a user who never opens the Advanced screen pays nothing for this.
     */
    private val bootstrapClient: OkHttpClient by lazy { OkHttpClient() }

    override fun lookup(hostname: String): List<InetAddress> = resolverFor(provider()).lookup(hostname)

    /** The resolver the current selection would use, for tests that must not touch the network. */
    internal fun resolverForTesting(): Dns = resolverFor(provider())

    private fun resolverFor(provider: DohProvider): Dns {
        val url = provider.url ?: return Dns.SYSTEM
        return resolvers.getOrPut(provider) {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(url.toHttpUrl())
                // The resolver's own address, so the bootstrap client never has to resolve the
                // resolver. Without these the first lookup would go back through the system
                // resolver — exactly what the user chose this to avoid.
                .bootstrapDnsHosts(provider.bootstrapAddresses())
                .build()
        }
    }

    private fun DohProvider.bootstrapAddresses(): List<InetAddress> = when (this) {
        DohProvider.OFF -> emptyList()
        DohProvider.CLOUDFLARE -> addresses("1.1.1.1", "1.0.0.1")
        DohProvider.GOOGLE -> addresses("8.8.8.8", "8.8.4.4")
        DohProvider.ADGUARD -> addresses("94.140.14.14", "94.140.15.15")
        DohProvider.QUAD9 -> addresses("9.9.9.9", "149.112.112.112")
    }

    /**
     * [InetAddress.getByName] on a literal does no lookup — it parses. Anything that somehow fails
     * to parse is dropped rather than thrown, because a bad constant here should cost a slower
     * bootstrap, not every request the app makes.
     */
    private fun addresses(vararg literals: String): List<InetAddress> =
        literals.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
}
