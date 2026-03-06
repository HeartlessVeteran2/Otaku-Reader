package app.komikku.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.komikku.core.database.dao.ChapterDao
import app.komikku.core.database.dao.MangaDao
import app.komikku.core.database.entity.ChapterEntity
import app.komikku.core.database.entity.MangaEntity
import app.komikku.core.database.util.DateConverter
import app.komikku.core.database.util.StringListConverter

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DateConverter::class, StringListConverter::class)
abstract class KomikkuDatabase : RoomDatabase() {

    abstract fun mangaDao(): MangaDao

    abstract fun chapterDao(): ChapterDao
}
