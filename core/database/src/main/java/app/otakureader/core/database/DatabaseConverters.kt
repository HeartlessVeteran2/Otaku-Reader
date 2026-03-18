package app.otakureader.core.database

import androidx.room.TypeConverter
import java.time.Instant

/** Room type converters for complex types stored in the database. */
class DatabaseConverters {
    @TypeConverter
    fun fromStringList(value: String): List<String> =
        if (value.isBlank()) emptyList()
        else value.split("|||")

    @TypeConverter
    fun toStringList(list: List<String>): String =
        list.joinToString("|||")

    @TypeConverter
    fun fromInstant(value: Instant?): Long? =
        value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? =
        value?.let { Instant.ofEpochMilli(it) }
}
