package app.otakureader.core.database

import androidx.room.TypeConverter
import app.otakureader.core.database.entity.StoredPerson
import kotlinx.serialization.json.Json
import java.time.Instant

/** Room type converters for complex types stored in the database. */
class DatabaseConverters {
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
     */
    @TypeConverter
    fun fromPersonList(value: String): List<StoredPerson> =
        if (value.isEmpty()) emptyList()
        else runCatching { json.decodeFromString<List<StoredPerson>>(value) }.getOrDefault(emptyList())

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
