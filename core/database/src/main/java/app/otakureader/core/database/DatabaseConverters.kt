package app.otakureader.core.database

import androidx.room.TypeConverter
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

    @TypeConverter
    fun fromInstant(value: Instant?): Long? =
        value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? =
        value?.let { Instant.ofEpochMilli(it) }

    private companion object {
        /** ASCII Unit Separator — a control character, so it cannot occur in real text. */
        const val LIST_SEPARATOR = "\u001F"
    }
}
