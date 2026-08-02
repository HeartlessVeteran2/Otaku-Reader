package app.otakureader.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Establishes, by experiment rather than by reading, what OkHttp actually does with a header a
 * NETWORK interceptor adds when the response is a cross-host redirect.
 *
 * This is the load-bearing assumption behind injecting page-image headers per hop
 * (`NetworkModule.providePageImageOkHttpClient`). If a header added on hop 1 survived into
 * hop 2, a source-supplied credential would ride to whatever host a CDN redirects to and
 * per-hop lookup would be no containment at all — the header would already be on the request
 * before the lookup ran.
 *
 * **It does not survive**, and the mechanism is specific: `RetryAndFollowUpInterceptor` sits
 * above the network interceptors and rebuilds the response with *its own* request
 * (`response.newBuilder().request(request)`) before deriving the follow-up. So the redirect is
 * built from the request as it stood before any network interceptor touched it. An
 * *application* interceptor would be above that boundary and its headers would propagate —
 * which is the bug this design exists to avoid.
 *
 * Kept as a test rather than a comment because it is an assertion about a third-party library's
 * internals: it holds for the OkHttp version in the catalog and an upgrade could change it
 * silently. A comment claiming this would rot invisibly; a failing test would not.
 */
class RedirectHeaderPropagationTest {

    @Test
    fun `a header added by a network interceptor is carried across a cross-host redirect`() {
        val target = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            start()
        }
        val origin = MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", target.url("/image.jpg").toString()),
            )
            start()
        }

        // Inject only on the first host, exactly as the page-image client does.
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                if (request.url.port == origin.port && request.header("X-Api-Key") == null) {
                    builder.header("X-Api-Key", "secret")
                }
                chain.proceed(builder.build())
            }
            .build()

        val body = client.newCall(Request.Builder().url(origin.url("/image.jpg")).build())
            .execute().use { it.body?.string() }

        // Guards against the assertion below passing vacuously: prove the redirect was actually
        // followed and the target actually served the request, so "no header" means "the header
        // was dropped", not "no request happened".
        assertEquals("the redirect must have been followed", "ok", body)
        assertEquals(1, target.requestCount)
        assertEquals("secret", origin.takeRequest().getHeader("X-Api-Key"))
        // The assertion that decides the design. If this is non-null, per-hop lookup alone is
        // not a containment boundary and the previous hop's header has to be removed explicitly.
        assertNull(
            "a credential injected for the origin must not reach the redirect target",
            target.takeRequest().getHeader("X-Api-Key"),
        )

        origin.shutdown()
        target.shutdown()
    }
}
