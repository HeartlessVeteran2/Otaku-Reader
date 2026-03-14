package app.otakureader.data.sync.di

import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.preferences.SyncPreferences
import app.otakureader.data.sync.DropboxSyncProvider
import app.otakureader.data.sync.GoogleDriveSyncProvider
import app.otakureader.data.sync.SyncManagerImpl
import app.otakureader.data.sync.WebDavSyncProvider
import app.otakureader.domain.sync.SyncManager
import app.otakureader.domain.sync.SyncProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncProviders(
        googleDriveSyncProvider: GoogleDriveSyncProvider,
        dropboxSyncProvider: DropboxSyncProvider,
        webDavSyncProvider: WebDavSyncProvider
    ): Set<SyncProvider> = setOf(
        googleDriveSyncProvider,
        dropboxSyncProvider,
        webDavSyncProvider
    )

    @Provides
    @Singleton
    fun provideSyncManager(
        mangaDao: MangaDao,
        chapterDao: ChapterDao,
        categoryDao: CategoryDao,
        syncPreferences: SyncPreferences,
        providers: Set<@JvmSuppressWildcards SyncProvider>
    ): SyncManager = SyncManagerImpl(
        mangaDao = mangaDao,
        chapterDao = chapterDao,
        categoryDao = categoryDao,
        syncPreferences = syncPreferences,
        providers = providers
    )
}
