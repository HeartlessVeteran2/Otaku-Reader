package app.otakureader.core.database

import app.otakureader.core.database.entity.StoredPerson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * People are stored as a JSON blob, so the round-trip and the failure mode are both worth pinning.
 *
 * The failure mode especially: this converter deliberately swallows a parse error and returns an
 * empty list, which is only defensible because `manga_metadata` is a disposable cache. If someone
 * later reaches for the same converter for data that is not re-fetchable, the test naming that
 * behaviour is what should make them stop.
 */
class PersonListConverterTest {

    private val converters = DatabaseConverters()

    private val people = listOf(
        StoredPerson(id = 1, name = "Gon Freecss", imageUrl = "https://x/1.jpg", role = "MAIN"),
        StoredPerson(id = 2, name = "Yoshihiro Togashi", imageUrl = null, role = "Story & Art"),
    )

    @Test
    fun `people survive a round trip through storage`() {
        val restored = converters.fromPersonList(converters.toPersonList(people))
        assertEquals(people, restored)
    }

    @Test
    fun `a null image and a null role round trip as null, not as empty strings`() {
        val sparse = listOf(StoredPerson(id = 3, name = "Unknown", imageUrl = null, role = null))
        val restored = converters.fromPersonList(converters.toPersonList(sparse))
        assertEquals(sparse, restored)
    }

    @Test
    fun `an empty list round trips`() {
        assertEquals(emptyList<StoredPerson>(), converters.fromPersonList(converters.toPersonList(emptyList())))
    }

    /** What a row written before the column existed backfills to. */
    @Test
    fun `the migration's default value reads back as an empty list`() {
        assertEquals(emptyList<StoredPerson>(), converters.fromPersonList("[]"))
    }

    @Test
    fun `an empty column reads back as an empty list rather than throwing`() {
        assertEquals(emptyList<StoredPerson>(), converters.fromPersonList(""))
    }

    /**
     * A Room read must not throw here. Every screen observing this manga collects a Flow off that
     * read, so an exception would not degrade one section — it would take down the details screen.
     */
    @Test
    fun `a malformed blob yields an empty list instead of propagating out of a Room read`() {
        assertEquals(emptyList<StoredPerson>(), converters.fromPersonList("{not json"))
        assertEquals(emptyList<StoredPerson>(), converters.fromPersonList("""{"id":1}"""))
    }

    /**
     * A blob written by a newer build carrying a field this one has never heard of still reads.
     * Without `ignoreUnknownKeys` the whole list would be discarded on a downgrade.
     */
    @Test
    fun `an unknown field does not discard the record`() {
        val restored = converters.fromPersonList(
            """[{"id":4,"name":"Killua","imageUrl":null,"role":"MAIN","hairColour":"silver"}]"""
        )
        assertEquals(listOf(StoredPerson(id = 4, name = "Killua", role = "MAIN")), restored)
    }
}
