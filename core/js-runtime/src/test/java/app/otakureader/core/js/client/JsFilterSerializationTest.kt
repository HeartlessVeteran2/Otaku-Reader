package app.otakureader.core.js.client

import app.otakureader.sourceapi.Filter
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.Filters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the translation of the app's filters into the array a JavaScript `search` receives.
 *
 * `fetchSearchManga` accepts a [FilterList] and previously discarded it, so every filtered
 * search silently ran unfiltered — results that look correct while ignoring what the user
 * asked for, which is worse than an outright failure because nothing signals it.
 */
class JsFilterSerializationTest {

    private fun parse(json: String): JsonArray = Json.parseToJsonElement(json) as JsonArray

    private fun JsonArray.names(): List<String> =
        map { (it as JsonObject)["name"]!!.jsonPrimitive.content }

    @Test
    fun `an empty filter list serializes to an empty array`() {
        assertEquals("[]", FilterList().toJsFilters())
    }

    /**
     * Untouched filters must not be emitted. Sending them makes a source treat "not selected"
     * as a real constraint, quietly narrowing results.
     */
    @Test
    fun `inactive filters are omitted`() {
        val filters = FilterList(
            listOf(
                Filters.SelectFilter("Status", arrayOf("Any", "Ongoing"), state = 0),
                Filters.TextFilter("Author", state = ""),
                Filters.CheckBoxFilter("Completed", state = false),
                Filters.TriStateFilter("Action", state = 0),
            ),
        )

        assertEquals("[]", filters.toJsFilters())
    }

    @Test
    fun `an active select filter carries its index`() {
        val filters = FilterList(
            listOf(Filters.SelectFilter("Status", arrayOf("Any", "Ongoing"), state = 1)),
        )

        val parsed = parse(filters.toJsFilters())

        assertEquals(1, parsed.size)
        val entry = parsed.single() as JsonObject
        assertEquals("select", entry["type"]!!.jsonPrimitive.content)
        assertEquals("Status", entry["name"]!!.jsonPrimitive.content)
        assertEquals("1", entry["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an active text filter is emitted as a quoted string`() {
        val filters = FilterList(listOf(Filters.TextFilter("Author", state = "Oda")))

        val entry = parse(filters.toJsFilters()).single() as JsonObject

        assertEquals("text", entry["type"]!!.jsonPrimitive.content)
        assertEquals("Oda", entry["state"]!!.jsonPrimitive.content)
    }

    /**
     * A quote in a filter value must not be able to break out of the JSON literal — the same
     * injection concern that governs how call arguments reach the engine.
     */
    @Test
    fun `a text filter containing quotes stays well-formed`() {
        val filters = FilterList(listOf(Filters.TextFilter("Author", state = """a "quoted" \ value""")))

        val entry = parse(filters.toJsFilters()).single() as JsonObject

        assertEquals("""a "quoted" \ value""", entry["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tri-state and checkbox filters are emitted when active`() {
        val filters = FilterList(
            listOf(
                Filters.CheckBoxFilter("Completed", state = true),
                Filters.TriStateFilter("Action", state = 2),
            ),
        )

        val parsed = parse(filters.toJsFilters())

        assertEquals(2, parsed.size)
        assertTrue(parsed.names().containsAll(listOf("Completed", "Action")))
    }

    @Test
    fun `only the active subset of a mixed list is emitted`() {
        val filters = FilterList(
            listOf(
                Filters.SelectFilter("Status", arrayOf("Any", "Ongoing"), state = 1),
                Filters.TextFilter("Author", state = ""),
                Filters.CheckBoxFilter("Completed", state = true),
                Filters.TriStateFilter("Action", state = 0),
            ),
        )

        val parsed = parse(filters.toJsFilters())

        assertEquals(listOf("Status", "Completed"), parsed.names())
    }

    @Test
    fun `headers and separators are skipped rather than serialized`() {
        val filters = FilterList(
            listOf(
                Filter.Header("Genres"),
                Filter.Separator(),
                Filters.CheckBoxFilter("Action", state = true),
            ),
        )

        val parsed = parse(filters.toJsFilters())

        assertEquals(listOf("Action"), parsed.names())
    }
}
