package app.otakureader.core.js.ipc;

/**
 * Callback the sidecar uses to perform HTTP, implemented in the main process.
 *
 * Network deliberately does NOT happen in the sidecar. Routing it back through the main
 * process keeps every request on the app's shared OkHttpClient, so certificate pinning, rate
 * limiting, the cookie jar and WebView-synced cookies/User-Agent all apply to JavaScript
 * sources exactly as they do to everything else — and a compromised sidecar has no
 * independent network path of its own.
 *
 * The extra hop costs a binder round-trip against a request that is already network-latency
 * bound, so it is not measurable in practice.
 */
interface IJsHttpBridge {

    /**
     * Execute one HTTP request.
     *
     * @param requestJson serialized request (url, method, headers, body)
     * @return serialized response (code, headers, body), or a serialized error
     */
    String execute(String requestJson);
}
