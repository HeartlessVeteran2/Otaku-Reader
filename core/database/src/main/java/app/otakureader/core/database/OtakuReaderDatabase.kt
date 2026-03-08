package app.otakureader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.TrackDao
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.ReadingHistoryEntity
import app.otakureader.core.database.entity.TrackEntity

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        TrackEntity::class,
        ReadingHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class OtakuReaderDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun trackDao(): TrackDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    
    companion object {
        const val DATABASE_NAME = "otakureader.db"
    }
}
