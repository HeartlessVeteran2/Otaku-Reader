package app.komikku.core.database

import androidx.room.TypeConverter

/** Room type converters for complex types stored in the database. */
class DatabaseConverters {
    @TypeConverter
    fun fromStringList(value: String): List<String> =
        if (value.isBlank()) emptyList()
        else value.split("|||")

    @TypeConverter
    fun toStringList(list: List<String>): String =
        list.joinToString("|||")
}
