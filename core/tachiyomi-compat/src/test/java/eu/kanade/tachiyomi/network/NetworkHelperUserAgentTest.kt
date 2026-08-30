package eu.kanade.tachiyomi.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.network.NetworkSettings
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How the Advanced-settings User-Agent override reaches loaded APK extensions (#1208).
 *
 * They never pass through the shared client's `UserAgentInterceptor`, because `HttpSource` sets the
 * header itself from [NetworkHelper.defaultUserAgentProvider]. That method is therefore the only
 * route in, and these cases pin its behaviour.
 */
@RunWith(AndroidJUnit4::class)
class NetworkHelperUserAgentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun helper(override: (() -> String?)?) =
        NetworkHelper(context, userAgentOverride = override)

    @Test
    fun `with no override the built-in identity is used`() {
        assertEquals(
            NetworkHelper.DEFAULT_USER_AGENT,
            helper(override = null).defaultUserAgentProvider(),
        )
    }

    @Test
    fun `an override replaces the built-in identity`() {
        assertEquals("Custom/1.0", helper { "Custom/1.0" }.defaultUserAgentProvider())
    }

    /**
     * Blank is how the settings screen represents "use the default" — it writes an empty string
     * rather than removing the key, so an empty value must not be sent as the identity. A source
     * receiving `User-Agent:` with nothing after it is worse than one receiving the default.
     */
    @Test
    fun `a blank override falls back to the built-in identity`() {
        assertEquals(
            NetworkHelper.DEFAULT_USER_AGENT,
            helper { "   " }.defaultUserAgentProvider(),
        )
    }

    /**
     * Read per call, not captured. The setting can change while the app runs, and an extension
     * that had already been handed a string would keep sending the old one.
     */
    @Test
    fun `the override is re-read on every call`() {
        var current = "First/1"
        val helper = helper { current }

        assertEquals("First/1", helper.defaultUserAgentProvider())
        current = "Second/2"
        assertEquals("Second/2", helper.defaultUserAgentProvider())
    }

    /**
     * The two constants must not drift. [NetworkHelper.DEFAULT_USER_AGENT] stays a `const` because
     * loaded extensions compile against it, while the value the rest of the app sends lives in
     * [NetworkSettings]. Nothing but this assertion keeps them equal, and a divergence would show
     * up as extensions and JavaScript sources presenting different identities to the same site —
     * which is exactly the mismatch that gets a Cloudflare clearance cookie rejected.
     */
    @Test
    fun `the built-in identity matches the one the rest of the app sends`() {
        assertEquals(NetworkSettings.DEFAULT_USER_AGENT, NetworkHelper.DEFAULT_USER_AGENT)
    }

    /**
     * The route the override actually travels, end to end.
     *
     * `defaultUserAgentProvider` being live is necessary but not sufficient: `HttpSource.headers`
     * is what every request is built from, and while it was `by lazy` it read the provider once per
     * source instance and cached the answer. Source instances outlive the settings screen, so the
     * override reached APK extensions only after a restart — and three places in this change said
     * otherwise. This asserts the whole path rather than the provider alone.
     *
     * Goes through Injekt because that is how a real extension resolves its `NetworkHelper`: the
     * property is `protected` and final, so there is nothing to inject by hand, and registering the
     * binding is exactly what `OtakuReaderApplication` does at startup.
     */
    @Test
    fun `an HttpSource picks up a User-Agent change without being recreated`() {
        var current = "First/1"
        Injekt.addSingletonFactory<NetworkHelper> { helper { current } }
        val source = StubHttpSource()

        assertEquals("First/1", source.headers["User-Agent"])
        current = "Second/2"
        assertEquals("Second/2", source.headers["User-Agent"])
    }

    /** Only [HttpSource.headers] is under test; the parse/request members are never called. */
    private class StubHttpSource : HttpSource() {
        override val name = "Test"
        override val baseUrl = "https://example.test"
        override val lang = "en"
        override val supportsLatest = false

        override fun popularMangaRequest(page: Int): Request = unused()
        override fun popularMangaParse(response: Response): MangasPage = unused()
        override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = unused()
        override fun searchMangaParse(response: Response): MangasPage = unused()
        override fun latestUpdatesRequest(page: Int): Request = unused()
        override fun latestUpdatesParse(response: Response): MangasPage = unused()
        override fun mangaDetailsParse(response: Response): SManga = unused()
        override fun chapterListParse(response: Response): List<SChapter> = unused()
        override fun pageListParse(response: Response): List<Page> = unused()
        override fun imageUrlParse(response: Response): String = unused()

        private fun unused(): Nothing = throw UnsupportedOperationException("not part of this test")
    }
}
