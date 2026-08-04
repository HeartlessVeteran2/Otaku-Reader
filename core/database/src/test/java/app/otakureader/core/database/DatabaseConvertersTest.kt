package app.otakureader.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list converter's job is a round trip, so every test here is one.
 *
 * The delimiter used to be `|||`, which is ordinary text a manga title could contain — a value
 * holding it would split into several on the way back out. `manga_metadata` is the first table with
 * a `List<String>` column, so nothing was ever written in the old format and there is no data to
 * migrate; the tests below pin the new encoding before that stops being true.
 */
class DatabaseConvertersTest {

    private val converters = DatabaseConverters()

    private fun roundTrip(list: List<String>): List<String> =
        converters.fromStringList(converters.toStringList(list))

    @Test
    fun `an empty list round-trips`() {
        assertEquals(emptyList<String>(), roundTrip(emptyList()))
    }

    @Test
    fun `a single value round-trips`() {
        assertEquals(listOf("Action"), roundTrip(listOf("Action")))
    }

    @Test
    fun `several values round-trip in order`() {
        assertEquals(listOf("Action", "Drama", "Fantasy"), roundTrip(listOf("Action", "Drama", "Fantasy")))
    }

    @Test
    fun `a value containing the old delimiter survives`() {
        // The regression this encoding exists for: under `|||` this came back as three values.
        assertEquals(listOf("weird|||title"), roundTrip(listOf("weird|||title")))
    }

    @Test
    fun `values containing pipes, commas and semicolons survive`() {
        // Every delimiter someone might reach for next.
        val awkward = listOf("a|b", "c,d", "e;f", "g\th", "i\nj")
        assertEquals(awkward, roundTrip(awkward))
    }

    /**
     * The separator itself cannot break the encoding, because it is removed on write.
     *
     * "A manga title will never contain 0x1F" is almost certainly true, and is exactly the sort of
     * assumption that is expensive to be wrong about. Stripping makes the round trip a guarantee:
     * the value loses the control character, which is not information, rather than splitting into
     * two values, which is corruption.
     */
    @Test
    fun `a value containing the separator itself cannot split the list`() {
        val result = roundTrip(listOf("before\u001Fafter", "second"))

        assertEquals(2, result.size)
        assertEquals("beforeafter", result[0])
        assertEquals("second", result[1])
    }

    @Test
    fun `unicode and emoji survive`() {
        val titles = listOf("僕のヒーローアカデミア", "Кага́я", "Berserk 🗡")
        assertEquals(titles, roundTrip(titles))
    }

    @Test
    fun `the stored form uses a control character rather than printable text`() {
        // Asserting the encoding, not just the round trip: a future change to a printable
        // delimiter would keep every round-trip test above green while reintroducing the bug.
        val stored = converters.toStringList(listOf("a", "b"))

        assertTrue(stored, stored.contains('\u001F'))
        assertEquals("a\u001Fb", stored)
    }
}
