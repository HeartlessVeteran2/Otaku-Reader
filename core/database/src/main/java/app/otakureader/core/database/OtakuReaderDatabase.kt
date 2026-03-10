package app.otakureader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaCategoryDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.ReadingHistoryEntity

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        ReadingHistoryEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class OtakuReaderDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun mangaCategoryDao(): MangaCategoryDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    
    companion object {
        const val DATABASE_NAME = "otakureader.db"
    }
}
