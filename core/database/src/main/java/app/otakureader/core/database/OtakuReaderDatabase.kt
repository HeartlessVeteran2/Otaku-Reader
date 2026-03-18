package app.otakureader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.FeedDao
import app.otakureader.core.database.dao.MangaCategoryDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.OpdsServerDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.dao.TrackerSyncDao
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.FeedItemEntity
import app.otakureader.core.database.entity.FeedSavedSearchEntity
import app.otakureader.core.database.entity.FeedSourceEntity
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.OpdsServerEntity
import app.otakureader.core.database.entity.ReadingHistoryEntity
import app.otakureader.core.database.entity.SyncConfigurationEntity
import app.otakureader.core.database.entity.TrackerSyncStateEntity

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        ReadingHistoryEntity::class,
        OpdsServerEntity::class,
        // Feed feature
        FeedItemEntity::class,
        FeedSourceEntity::class,
        FeedSavedSearchEntity::class,
        // Tracker sync feature
        TrackerSyncStateEntity::class,
        SyncConfigurationEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class OtakuReaderDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun mangaCategoryDao(): MangaCategoryDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun opdsServerDao(): OpdsServerDao
    
    // New DAOs
    abstract fun feedDao(): FeedDao
    abstract fun trackerSyncDao(): TrackerSyncDao
    
    companion object {
        const val DATABASE_NAME = "otakureader.db"
    }
}
