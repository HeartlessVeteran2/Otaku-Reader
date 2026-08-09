package app.otakureader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.otakureader.core.database.dao.AchievementDao
import app.otakureader.core.database.dao.BookmarkCollectionDao
import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.DataUsageDao
import app.otakureader.core.database.dao.DownloadQueueDao
import app.otakureader.core.database.dao.DynamicCategoryRuleDao
import app.otakureader.core.database.dao.FeedDao
import app.otakureader.core.database.dao.MangaAlternativeSourceDao
import app.otakureader.core.database.dao.MangaAniListLinkDao
import app.otakureader.core.database.dao.MangaCategoryDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.MangaMetadataDao
import app.otakureader.core.database.dao.OpdsServerDao
import app.otakureader.core.database.dao.PageBookmarkDao
import app.otakureader.core.database.dao.ReaderCommentDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.dao.ReadingListDao
import app.otakureader.core.database.dao.ReadingStreakDao
import app.otakureader.core.database.dao.RecommendationDao
import app.otakureader.core.database.dao.SyncQueueDao
import app.otakureader.core.database.dao.TrackEntryDao
import app.otakureader.core.database.dao.TrackerSyncDao
import app.otakureader.core.database.dao.UpdateErrorDao
import app.otakureader.core.database.dao.UpdateRunSummaryDao
import app.otakureader.core.database.entity.AchievementEntity
import app.otakureader.core.database.entity.BookmarkCollectionEntity
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.DataUsageEntity
import app.otakureader.core.database.entity.DownloadQueueEntity
import app.otakureader.core.database.entity.DynamicCategoryRuleEntity
import app.otakureader.core.database.entity.FeedItemEntity
import app.otakureader.core.database.entity.FeedSavedSearchEntity
import app.otakureader.core.database.entity.FeedSourceEntity
import app.otakureader.core.database.entity.MangaAlternativeSourceEntity
import app.otakureader.core.database.entity.MangaAniListLinkEntity
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.MangaFtsEntity
import app.otakureader.core.database.entity.MangaMetadataEntity
import app.otakureader.core.database.entity.OpdsServerEntity
import app.otakureader.core.database.entity.PageBookmarkEntity
import app.otakureader.core.database.entity.ReaderCommentEntity
import app.otakureader.core.database.entity.ReadingHistoryEntity
import app.otakureader.core.database.entity.ReadingListEntity
import app.otakureader.core.database.entity.ReadingListItemEntity
import app.otakureader.core.database.entity.ReadingStreakEntity
import app.otakureader.core.database.entity.RecommendationEntity
import app.otakureader.core.database.entity.SyncConfigurationEntity
import app.otakureader.core.database.entity.SyncQueueEntity
import app.otakureader.core.database.entity.TrackEntryEntity
import app.otakureader.core.database.entity.TrackerSyncStateEntity
import app.otakureader.core.database.entity.UpdateErrorEntity
import app.otakureader.core.database.entity.UpdateRunSummaryEntity

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        ReadingHistoryEntity::class,
        ReadingStreakEntity::class,
        OpdsServerEntity::class,
        // Feed feature
        FeedItemEntity::class,
        FeedSourceEntity::class,
        FeedSavedSearchEntity::class,
        // Tracker sync feature
        TrackerSyncStateEntity::class,
        SyncConfigurationEntity::class,
        // Page bookmarks
        PageBookmarkEntity::class,
        // Reading lists
        ReadingListEntity::class,
        ReadingListItemEntity::class,
        // Download queue persistence
        DownloadQueueEntity::class,
        // Track entries (persisted tracker state)
        TrackEntryEntity::class,
        // Dynamic category rules (#881)
        DynamicCategoryRuleEntity::class,
        // Recommendation cache (#895)
        RecommendationEntity::class,
        // Reading achievements (#955)
        AchievementEntity::class,
        // Data usage dashboard (#956)
        DataUsageEntity::class,
        // Reader progress sync queue (#958)
        SyncQueueEntity::class,
        // FTS index for library search (#997/Phase5)
        MangaFtsEntity::class,
        // Library update run history diagnostics (#1041)
        UpdateRunSummaryEntity::class,
        // Alternative-source linking for cross-source duplicate merge (#1053)
        MangaAlternativeSourceEntity::class,
        // Local reader comments (chapter + book scoped)
        ReaderCommentEntity::class,
        // Bookmark collections (#1128)
        BookmarkCollectionEntity::class,
        // Current unresolved per-manga library update errors (Update Errors screen)
        UpdateErrorEntity::class,
        // Cached AniList metadata for the details screen (Stage 5b)
        MangaMetadataEntity::class,
        // Which AniList media a manga is, durable across metadata cache clears (Stage 5b)
        MangaAniListLinkEntity::class,
    ],
    version = 45,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class OtakuReaderDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun mangaCategoryDao(): MangaCategoryDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun readingStreakDao(): ReadingStreakDao
    abstract fun opdsServerDao(): OpdsServerDao
    abstract fun pageBookmarkDao(): PageBookmarkDao

    // Feed + tracker sync + reading list DAOs
    abstract fun feedDao(): FeedDao
    abstract fun trackerSyncDao(): TrackerSyncDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun trackEntryDao(): TrackEntryDao
    abstract fun dynamicCategoryRuleDao(): DynamicCategoryRuleDao
    abstract fun recommendationDao(): RecommendationDao
    abstract fun achievementDao(): AchievementDao
    abstract fun dataUsageDao(): DataUsageDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun updateRunSummaryDao(): UpdateRunSummaryDao
    abstract fun mangaAlternativeSourceDao(): MangaAlternativeSourceDao
    abstract fun readerCommentDao(): ReaderCommentDao
    abstract fun bookmarkCollectionDao(): BookmarkCollectionDao
    abstract fun updateErrorDao(): UpdateErrorDao
    abstract fun mangaMetadataDao(): MangaMetadataDao
    abstract fun mangaAniListLinkDao(): MangaAniListLinkDao

    companion object {
        const val DATABASE_NAME = "otakureader.db"
    }
}
