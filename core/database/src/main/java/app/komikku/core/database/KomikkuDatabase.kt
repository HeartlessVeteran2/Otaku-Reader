package app.komikku.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.komikku.core.database.dao.CategoryDao
import app.komikku.core.database.dao.ChapterDao
import app.komikku.core.database.dao.MangaCategoryDao
import app.komikku.core.database.dao.MangaDao
import app.komikku.core.database.dao.ReadingHistoryDao
import app.komikku.core.database.entity.CategoryEntity
import app.komikku.core.database.entity.ChapterEntity
import app.komikku.core.database.entity.MangaCategoryEntity
import app.komikku.core.database.entity.MangaEntity
import app.komikku.core.database.entity.ReadingHistoryEntity

/**
 * Main Room database for Komikku.
 * Version history is tracked via Room migrations and exported schemas in /schemas.
 */
@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        ReadingHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class KomikkuDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun mangaCategoryDao(): MangaCategoryDao
    abstract fun readingHistoryDao(): ReadingHistoryDao

    companion object {
        const val DATABASE_NAME = "komikku.db"
    }
}
