package app.otakureader.core.js.ipc;

/**
 * Callback the sidecar uses to perform HTTP, implemented in the main process.
 *
 * Network deliberately does NOT happen in the sidecar. The engine runs in an isolated process
 * with no permissions of its own — it has no INTERNET permission and could not open a socket
 * even if it wanted to — so this bridge is the only network path available to a JavaScript
 * source. That makes certificate pinning, rate limiting, the cookie jar and WebView-synced
 * cookies/User-Agent enforced constraints rather than conventions.
 *
 * The extra hop costs a binder round-trip against a request that is already network-latency
 * bound, so it is not measurable in practice.
 */
interface IJsHttpBridge {

    /**
     * Execute one HTTP request.
     *
     * Returns through a pipe rather than as a String for the same reason the engine's own
     * results do: binder transactions cap near 1 MB, and page HTML routinely exceeds that.
     * Returning a String here would have reintroduced, on the response path, exactly the
     * limit the engine result path was designed to avoid.
     *
     * @param requestJson serialized request (url, method, headers, body)
     * @return read end of a pipe carrying the serialized response; never null
     */
    ParcelFileDescriptor execute(String requestJson);
}
