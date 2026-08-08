package app.otakureader.data.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The query is a raw string mixing two kinds of `$`, which is easy to get backwards.
 *
 * `${'$'}id` has to survive into the sent query as a literal dollar, because it is a *GraphQL*
 * variable the server binds. `$PEOPLE_PER_PAGE` is a *Kotlin* template that must be substituted
 * before sending. Writing the second in the first's escaped form compiles, reads correctly at a
 * glance, and ships `perPage: ${PEOPLE_PER_PAGE}` to AniList — which fails at the server, far from
 * the mistake.
 */
class AniListMetadataQueryTest {

    /**
     * The `25` is written out rather than read from `PEOPLE_PER_PAGE` on purpose.
     *
     * A test that computes its expectation from the same constant as the code cannot tell a
     * correct value from a wrong one — bump the constant to 500 and it still passes, having
     * verified only that Kotlin can interpolate. Restating the number makes changing the page size
     * a deliberate act that has to touch this file, which is where the reason for the limit is
     * written down. (It is also the only option here: the constant is file-private.)
     */
    @Test
    fun `the page size is substituted, not sent as a literal template`() {
        assertTrue("perPage must carry a number", METADATA_QUERY.contains("perPage: 25"))
        assertFalse(
            "an unsubstituted Kotlin template would reach AniList verbatim",
            METADATA_QUERY.contains("PEOPLE_PER_PAGE"),
        )
    }

    @Test
    fun `the GraphQL variable survives as a literal dollar`() {
        assertTrue("\$id must reach the server unsubstituted", METADATA_QUERY.contains("\$id"))
        assertTrue(METADATA_QUERY.contains("query (\$id: Int)"))
    }

    @Test
    fun `characters and staff are requested with the role each carries`() {
        // Role lives on the edge, not the node: the same person is MAIN here and BACKGROUND
        // elsewhere, so asking the node for it would return nothing.
        assertTrue(METADATA_QUERY.contains("characters(perPage: 25, sort: [ROLE, RELEVANCE])"))
        assertTrue(METADATA_QUERY.contains("staff(perPage: 25, sort: [RELEVANCE])"))
        assertTrue(METADATA_QUERY.contains("edges { role node { id name { full } image { large } } }"))
    }
}
