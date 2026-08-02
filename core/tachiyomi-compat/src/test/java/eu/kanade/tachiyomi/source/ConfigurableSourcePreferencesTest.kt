package eu.kanade.tachiyomi.source

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * Guards the host-side preference plumbing that loaded extension APKs depend on.
 *
 * Extensions do not link against the app's Hilt graph — they resolve host services through
 * Injekt, which is a service locator. Anything an extension asks for at runtime must have
 * been registered first; a missing registration throws at the call site rather than failing
 * to compile, so nothing catches it until a real extension is loaded on a device.
 *
 * [ConfigurableSource.getSourcePreferences] resolves `Application` that way, and many real
 * extensions read their preferences from a constructor or a `baseUrl` getter. With no
 * `Application` registered, those extensions threw during instantiation and the loader
 * reported them as having no valid sources — which presented as the app having no sources
 * at all. `OtakuReaderApplication` now registers the binding; these tests pin the contract
 * that binding has to satisfy.
 */
class ConfigurableSourcePreferencesTest {

    private class TestConfigurableSource(
        override val id: Long,
        override val name: String = "Test Source",
    ) : ConfigurableSource {
        override suspend fun getMangaDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
        override suspend fun getRelatedMangaList(
            manga: SManga,
            exceptionHandler: (Throwable) -> Unit,
            pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
        ) = Unit

        override fun setupPreferenceScreen(screen: PreferenceScreen) = Unit
    }

    companion object {
        // Injekt is a process-global registry and `addSingletonFactory` caches the first
        // resolved instance for the lifetime of the JVM. Registering a fresh mock in @Before
        // therefore does NOT replace what a previous test already resolved — every test after
        // the first would assert against a stale instance and fail on identity.
        //
        // So the mocks are created once and registered once, and per-test isolation comes from
        // clearing recorded calls rather than from rebuilding the graph.
        private val preferences: SharedPreferences = mockk(relaxed = true)
        private val application: Application = mockk(relaxed = true)

        @JvmStatic
        @BeforeClass
        fun registerInjektBindings() {
            Injekt.addSingletonFactory<Application> { application }
        }
    }

    @Before
    fun setUp() {
        clearMocks(application, preferences, answers = false, recordedCalls = true, verificationMarks = true)
        every { application.getSharedPreferences(any(), any()) } returns preferences
    }

    @Test
    fun `Application is resolvable from Injekt`() {
        // The whole failure mode this guards against is this lookup throwing.
        assertSame(application, Injekt.get<Application>())
    }

    @Test
    fun `getSourcePreferences scopes preferences to the source id`() {
        val source = TestConfigurableSource(id = 9001L)

        val result = source.getSourcePreferences()

        assertSame(preferences, result)
        verify { application.getSharedPreferences("source_9001", Context.MODE_PRIVATE) }
    }

    @Test
    fun `preferenceKey is derived from the source id`() {
        assertEquals("source_42", TestConfigurableSource(id = 42L).preferenceKey())
    }

    @Test
    fun `two sources do not share a preference store`() {
        val first = TestConfigurableSource(id = 1L)
        val second = TestConfigurableSource(id = 2L)

        first.getSourcePreferences()
        second.getSourcePreferences()

        verify { application.getSharedPreferences("source_1", Context.MODE_PRIVATE) }
        verify { application.getSharedPreferences("source_2", Context.MODE_PRIVATE) }
    }

    /**
     * `sourcePreferences()` is the legacy free-function form that older extensions link
     * against directly. It must stay on the same code path as the interface method, or a
     * source could observe different preferences depending on which form it happened to
     * call — a split that would only ever show up as mystifying settings loss at runtime.
     */
    @Test
    fun `legacy sourcePreferences delegates to getSourcePreferences`() {
        val source = TestConfigurableSource(id = 7L)

        val viaExtension = source.sourcePreferences()
        val viaInterface = source.getSourcePreferences()

        assertSame(viaInterface, viaExtension)
        verify(exactly = 2) { application.getSharedPreferences("source_7", Context.MODE_PRIVATE) }
    }

    @Test
    fun `keyed sourcePreferences resolves the requested store`() {
        val result = sourcePreferences("custom_key")

        assertSame(preferences, result)
        verify { application.getSharedPreferences("custom_key", Context.MODE_PRIVATE) }
    }

    /**
     * Mirrors how extensions actually fail: reading preferences from the constructor. Before
     * the Injekt binding existed this threw, the loader caught it, and the extension silently
     * reported zero sources.
     */
    @Test
    fun `source reading preferences during construction does not throw`() {
        class EagerSource : ConfigurableSource {
            override val id: Long = 123L
            override val name: String = "Eager"
            val baseUrl: String = getSourcePreferences().getString("domain", "https://example.org")
                ?: "https://example.org"

            override suspend fun getMangaDetails(manga: SManga): SManga = manga
            override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
            override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
            override suspend fun getRelatedMangaList(
                manga: SManga,
                exceptionHandler: (Throwable) -> Unit,
                pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
            ) = Unit

            override fun setupPreferenceScreen(screen: PreferenceScreen) = Unit
        }

        every { preferences.getString("domain", any()) } returns "https://example.org"

        val source = EagerSource()

        assertTrue(source.baseUrl.isNotEmpty())
        verify { application.getSharedPreferences("source_123", Context.MODE_PRIVATE) }
    }
}
