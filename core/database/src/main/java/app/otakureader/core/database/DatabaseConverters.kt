package app.otakureader.core.database

import androidx.room.TypeConverter
import app.otakureader.core.database.entity.StoredPerson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Room type converters for complex types stored in the database.
 *
 * [onDecodeFailure] exists so the JSON converter can *signal* a failure it deliberately swallows.
 * It has a default, which is what lets Room keep constructing this class with no arguments —
 * Kotlin generates a parameterless constructor when every parameter has a default — while tests
 * can pass a recorder and assert the signal actually fires.
 */
class DatabaseConverters(
    private val onDecodeFailure: (payloadLength: Int, throwable: Throwable) -> Unit =
        ::logPersonDecodeFailure,
) {
    /**
     * Lists are joined with ASCII **Unit Separator** (`0x1F`), the control character defined for
     * exactly this job.
     *
     * The previous delimiter was `|||`, which is ordinary text: a tag or title containing it would
     * split into several values on the way back out, and the cached row would stop matching what
     * was stored. That was latent rather than live — `manga_metadata` is the first and only table
     * with a `List<String>` column, so no data has ever been written in the old format and this
     * costs no migration. Fixed now because the price only goes up.
     *
     * The separator is *stripped* on write rather than assumed absent. "A manga title will never
     * contain 0x1F" is almost certainly true and is exactly the kind of assumption that is
     * expensive to be wrong about; removing it makes the round-trip a guarantee instead.
     */
    @TypeConverter
    fun fromStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList()
        else value.split(LIST_SEPARATOR)

    @TypeConverter
    fun toStringList(list: List<String>): String =
        list.joinToString(LIST_SEPARATOR) { it.replace(LIST_SEPARATOR, "") }

    /**
     * People are stored as JSON, not as parallel delimited columns.
     *
     * The tag columns above are the counter-example: `tagNames` and `tagRanks` are two lists that
     * must stay the same length, and the mapper has to zip-and-drop to survive them disagreeing.
     * That is tolerable for two fields. A person has four, and there are two such lists, so the
     * same encoding would mean eight columns and eight chances for the invariant to slip. JSON
     * keeps each record whole, so a field cannot go missing independently of its record.
     *
     * Not a child table for the reason tags are not one either: the access pattern is "read every
     * person for this manga, always together, never queried across manga", so a join buys nothing.
     *
     * ### A malformed blob yields an empty list rather than throwing
     *
     * This is the one place that judgement is safe, and only because `manga_metadata` is a
     * disposable seven-day cache with an upstream that can always be re-fetched. A parse failure —
     * a field renamed, a row written by a newer version — must not propagate out of a Room read
     * and take down every screen that observes this manga; the section simply does not render and
     * the next refresh repairs it. **Do not copy this to a table that owns its data**: there,
     * swallowing a parse failure turns corruption into silent data loss.
     *
     * Swallowed, but not unobserved. One row failing is a curiosity; *every* row failing is an
     * incompatible change to [StoredPerson], and nothing else would report it — Room validates a
     * table's columns, not the shape of JSON inside a TEXT one, so the carousels would stop
     * appearing everywhere with no other symptom. Hence [onDecodeFailure].
     */
    @TypeConverter
    fun fromPersonList(value: String): List<StoredPerson> =
        if (value.isEmpty()) {
            emptyList()
        } else {
            // SerializationException specifically, not runCatching. runCatching swallows every
            // Throwable, so an OutOfMemoryError or StackOverflowError on a pathological blob would
            // be reported to the user as "this manga has no characters" while the process is
            // actually in trouble. Only a decoding failure is the tolerable case, and
            // SerializationException is exactly that — malformed, truncated, or a shape this build
            // no longer understands.
            try {
                json.decodeFromString<List<StoredPerson>>(value)
            } catch (e: SerializationException) {
                onDecodeFailure(value.length, e)
                emptyList()
            }
        }

    @TypeConverter
    fun toPersonList(list: List<StoredPerson>): String =
        json.encodeToString(list)

    @TypeConverter
    fun fromInstant(value: Instant?): Long? =
        value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? =
        value?.let { Instant.ofEpochMilli(it) }

    private companion object {
        /** ASCII Unit Separator — a control character, so it cannot occur in real text. */
        const val LIST_SEPARATOR = "\u001F"

        /**
         * `ignoreUnknownKeys` so a blob written by a newer version, carrying a field this build
         * has never heard of, still reads back rather than being discarded wholesale.
         */
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The default [DatabaseConverters.onDecodeFailure]: a warning carrying the payload's *length*
 * rather than the payload.
 *
 * Warning and not debug, deliberately. `Logger.d` compiles out in release builds, and a diagnostic
 * that is absent exactly where the systemic failure would happen is not a diagnostic. This is rare
 * enough that it cannot become log spam — and if it is not rare, that is the thing worth knowing.
 *
 * The blob itself is not logged. It holds character and staff names for one manga, which identifies
 * the title, and what someone reads does not belong in logcat. Length plus the exception already
 * distinguishes truncation from a shape change, which is what a diagnosis turns on.
 */
private fun logPersonDecodeFailure(payloadLength: Int, throwable: Throwable) {
    android.util.Log.w(
        "DatabaseConverters",
        "Discarding an undecodable cached person list (payload length=$payloadLength); " +
            "the section renders empty until the next metadata refresh.",
        throwable,
    )
}
