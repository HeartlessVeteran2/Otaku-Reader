package app.otakureader.data.tracking.tracker

import app.otakureader.data.tracking.api.AniListGraphQlQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down how GraphQL variables are serialized.
 *
 * AniList validates variables against the types the operation declares, and rejects the whole
 * document on a mismatch. The previous `Map<String, String>` sent every value quoted, so
 * `mediaId` arrived as `"12345"` against a declared `Int` — every update failed server-side
 * while the client reported success, because `update()` swallowed the error and returned its
 * input unchanged.
 *
 * Asserting on the serialized JSON rather than on the Kotlin object is the point: the Kotlin
 * side looked correct under the old type too. Only the wire format showed the bug.
 */
class AniListVariableTypingTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `numeric variables serialize unquoted`() {
        val query = AniListGraphQlQuery(
            query = "mutation {}",
            variables = buildJsonObject {
                put("mediaId", 12345L)
                put("progress", 7)
                put("score", 8.5f)
            },
        )

        val encoded = json.encodeToString(AniListGraphQlQuery.serializer(), query)

        assertTrue("mediaId must not be quoted: $encoded", encoded.contains("\"mediaId\":12345"))
        assertTrue("progress must not be quoted: $encoded", encoded.contains("\"progress\":7"))
        assertTrue("score must not be quoted: $encoded", encoded.contains("\"score\":8.5"))
    }

    @Test
    fun `string variables stay quoted`() {
        // The fix must not overcorrect: enum and search values are genuinely strings, and
        // unquoting them would break the query in the opposite direction.
        val query = AniListGraphQlQuery(
            query = "query {}",
            variables = buildJsonObject {
                put("search", "Berserk")
                put("status", "CURRENT")
            },
        )

        val encoded = json.encodeToString(AniListGraphQlQuery.serializer(), query)

        assertTrue("search must stay quoted: $encoded", encoded.contains("\"search\":\"Berserk\""))
        assertTrue("status must stay quoted: $encoded", encoded.contains("\"status\":\"CURRENT\""))
    }
}
