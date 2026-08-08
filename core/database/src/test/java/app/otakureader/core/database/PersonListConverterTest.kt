package app.otakureader.core.database

import app.otakureader.core.database.entity.StoredExternalLink
import app.otakureader.core.database.entity.StoredPerson
import app.otakureader.core.database.entity.StoredRelation
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

    private val failures = mutableListOf<Pair<Int, Throwable>>()
    private val converters = DatabaseConverters { length, throwable -> failures += length to throwable }

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
     * The discard has to be *observable*, not merely survivable.
     *
     * If [StoredPerson] ever changes shape incompatibly, every row starts failing at once and
     * nothing else reports it — Room validates the table's columns, not the JSON inside a TEXT
     * one, so the carousels would vanish app-wide with no other symptom. A test that only asserts
     * "returns empty" passes with the signalling deleted, which is the whole point of this one.
     */
    @Test
    fun `a discarded blob is signalled, with its length and not its contents`() {
        converters.fromPersonList("{not json")

        assertEquals(1, failures.size)
        assertEquals("the payload length is what gets reported", "{not json".length, failures.single().first)
    }

    @Test
    fun `a blob that decodes cleanly signals nothing`() {
        converters.fromPersonList(converters.toPersonList(people))
        converters.fromPersonList("[]")
        converters.fromPersonList("")

        assertEquals("only a failure is worth reporting", emptyList<Any>(), failures)
    }

    @Test
    fun `relations survive a round trip through storage`() {
        val relations = listOf(
            StoredRelation(anilistId = 1, title = "Hunter x Hunter", coverImage = "https://x/1.jpg",
                format = "MANGA", relationType = "SEQUEL"),
            StoredRelation(anilistId = 2, title = "Level E"),
        )
        assertEquals(relations, converters.fromRelationList(converters.toRelationList(relations)))
    }

    @Test
    fun `external links survive a round trip through storage`() {
        val links = listOf(StoredExternalLink(url = "https://example.test/a", site = "Official Site"))
        assertEquals(links, converters.fromExternalLinkList(converters.toExternalLinkList(links)))
    }

    /**
     * The three list converters share one body, so the discard policy cannot drift between them.
     * If one is ever hand-rolled back to `runCatching`, this is what should notice.
     */
    @Test
    fun `every list converter discards a malformed blob and signals it`() {
        converters.fromPersonList("{not json")
        converters.fromRelationList("{not json")
        converters.fromExternalLinkList("{not json")

        assertEquals(3, failures.size)
    }

    @Test
    fun `every list converter backfills the migration default to an empty list`() {
        assertEquals(emptyList<StoredRelation>(), converters.fromRelationList("[]"))
        assertEquals(emptyList<StoredExternalLink>(), converters.fromExternalLinkList("[]"))
        assertEquals(emptyList<Any>(), failures)
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
