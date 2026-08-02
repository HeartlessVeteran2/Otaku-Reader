package app.otakureader.core.js.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the wire format between the app and the engine sidecar.
 *
 * This is the one place the two processes can silently disagree — they compile against the
 * same types, but a source's JSON is untrusted input that neither side controls. The sidecar
 * itself cannot run in a JVM unit test (it needs the Android service and the native engine),
 * so the encoding is the part worth pinning here.
 */
class JsProtocolTest {

    // --- tolerance of real-world source output --------------------------------------------

    /**
     * Extensions emit whatever their author felt like emitting. Unknown fields must be ignored
     * rather than failing the parse, or a source that adds a field upstream breaks in this app
     * while working everywhere else.
     */
    @Test
    fun `unknown fields are ignored`() {
        val json = """
            {"list":[{"name":"A","link":"/a","imageUrl":"x","somethingNew":42}],
             "hasNextPage":true,"alsoNew":"ignored"}
        """.trimIndent()

        val page = JsProtocol.json.decodeFromString<JsMangaListDto>(json)

        assertEquals(1, page.list.size)
        assertEquals("A", page.list.first().name)
        assertTrue(page.hasNextPage)
    }

    /** Missing optional fields are common; they must default rather than throw. */
    @Test
    fun `absent fields fall back to defaults`() {
        val page = JsProtocol.json.decodeFromString<JsMangaListDto>("""{"list":[{"name":"A"}]}""")

        val manga = page.list.single()
        assertEquals("A", manga.name)
        assertEquals("", manga.link)
        assertEquals(null, manga.imageUrl)
        assertEquals(emptyList<String>(), manga.genre)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `an empty result decodes to an empty page`() {
        val page = JsProtocol.json.decodeFromString<JsMangaListDto>("""{"list":[]}""")

        assertTrue(page.list.isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `explicit nulls are tolerated`() {
        val page = JsProtocol.json.decodeFromString<JsMangaListDto>(
            """{"list":[{"name":"A","link":"/a","imageUrl":null,"description":null}]}""",
        )

        assertEquals(null, page.list.single().imageUrl)
    }

    // --- detail and chapters ---------------------------------------------------------------

    @Test
    fun `detail decodes chapters`() {
        val json = """
            {"name":"Title","link":"/m","status":1,"genre":["Action","Drama"],
             "chapters":[{"name":"Ch 1","url":"/c1","dateUpload":"1700000000000","scanlator":"S"},
                         {"name":"Ch 2","url":"/c2"}]}
        """.trimIndent()

        val detail = JsProtocol.json.decodeFromString<JsMangaDetailDto>(json)

        assertEquals("Title", detail.name)
        assertEquals(listOf("Action", "Drama"), detail.genre)
        assertEquals(2, detail.chapters.size)
        assertEquals("1700000000000", detail.chapters.first().dateUpload)
        // Second chapter omits both optional fields entirely.
        assertEquals(null, detail.chapters[1].dateUpload)
        assertEquals(null, detail.chapters[1].scanlator)
    }

    // --- pages ------------------------------------------------------------------------------

    @Test
    fun `page list decodes with and without headers`() {
        val pages = JsProtocol.json.decodeFromString<List<JsPageDto>>(
            """[{"url":"https://a/1.jpg","headers":{"Referer":"https://a"}},{"url":"https://a/2.jpg"}]""",
        )

        assertEquals(2, pages.size)
        assertEquals("https://a", pages[0].headers["Referer"])
        assertTrue(
            "a page with no headers must decode to an empty map, not null",
            pages[1].headers.isEmpty(),
        )
    }

    // --- results and errors ------------------------------------------------------------------

    /**
     * Failures travel in-band rather than as binder exceptions, so a broken script is an
     * ordinary source failure instead of something that crosses the process boundary as a
     * crash. The kind has to survive the round-trip for the client to classify it.
     */
    @Test
    fun `error results round-trip with their kind`() {
        JsErrorKind.entries.forEach { kind ->
            val original = JsCallResult(ok = false, error = "boom", errorKind = kind)

            val restored = JsProtocol.json.decodeFromString<JsCallResult>(
                JsProtocol.json.encodeToString(original),
            )

            assertEquals(kind, restored.errorKind)
            assertFalse(restored.ok)
            assertEquals("boom", restored.error)
        }
    }

    @Test
    fun `success results carry their payload verbatim`() {
        val payload = """{"list":[],"hasNextPage":false}"""
        val original = JsCallResult(ok = true, data = payload)

        val restored = JsProtocol.json.decodeFromString<JsCallResult>(
            JsProtocol.json.encodeToString(original),
        )

        assertTrue(restored.ok)
        assertEquals(payload, restored.data)
    }

    // --- http bridge --------------------------------------------------------------------------

    @Test
    fun `http request and response round-trip`() {
        val request = JsHttpRequest(
            url = "https://example.org/api",
            method = "POST",
            headers = mapOf("Referer" to "https://example.org"),
            body = """{"q":"test"}""",
        )

        val restored = JsProtocol.json.decodeFromString<JsHttpRequest>(
            JsProtocol.json.encodeToString(request),
        )

        assertEquals(request, restored)
    }

    /** A quoted/escaped body must survive, since search queries routinely contain both. */
    @Test
    fun `bodies containing quotes and backslashes survive encoding`() {
        val nasty = """{"q":"a \"quoted\" \\ value"}"""
        val request = JsHttpRequest(url = "https://e.org", body = nasty)

        val restored = JsProtocol.json.decodeFromString<JsHttpRequest>(
            JsProtocol.json.encodeToString(request),
        )

        assertEquals(nasty, restored.body)
    }

    // --- source config -------------------------------------------------------------------------

    @Test
    fun `source config round-trips including preferences`() {
        val config = JsSourceConfig(
            id = "en.example",
            name = "Example",
            baseUrl = "https://example.org",
            lang = "en",
            isNsfw = true,
            preferences = mapOf("domain" to "https://alt.example.org"),
        )

        val restored = JsProtocol.json.decodeFromString<JsSourceConfig>(
            JsProtocol.json.encodeToString(config),
        )

        assertEquals(config, restored)
    }
}
