package app.otakureader.core.network.cloudflare

/**
 * Solves a Cloudflare browser challenge for a host, by whatever means the app has available.
 *
 * An interface here rather than a direct call because solving one means showing a WebView, and
 * `:core:network` must not depend on a UI module. `:core:webview` provides the implementation and
 * the binding lives in the app — the same shape as [app.otakureader.core.network.BytesRecorder],
 * which exists for exactly this reason.
 */
interface CloudflareChallengeSolver {

    /**
     * Present the challenge for [url] and suspend until it is resolved or abandoned.
     *
     * Implementations must **coalesce by host**: a blocked page triggers many parallel image
     * requests, and every one of them will arrive here at once. Opening a WebView per request
     * would bury the user under a stack of identical screens, all solving the same challenge.
     *
     * @return true when clearance was obtained, false when the user gave up or it timed out.
     */
    suspend fun solve(host: String, url: String): Boolean

    /**
     * The User-Agent that solved [host]'s challenge, or null if it has never been solved.
     *
     * Cloudflare binds clearance to the User-Agent that earned it, so a request replaying the
     * cookie under a different one is challenged again. This lets the network layer send the
     * same identity the WebView used.
     *
     * Non-suspending because it is read on every request from inside an interceptor, which is a
     * blocking call on an OkHttp dispatcher thread — a suspending disk read there would put I/O
     * on the hot path of every image in a chapter.
     */
    fun solvedUserAgent(host: String): String?
}
