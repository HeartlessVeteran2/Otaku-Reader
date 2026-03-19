package app.otakureader.data.sync.di

import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.preferences.SyncPreferences
import app.otakureader.data.sync.SelfHostedSyncProvider
import app.otakureader.data.sync.SyncManagerImpl
import app.otakureader.data.sync.remote.SelfHostedSyncApiFactory
import app.otakureader.domain.sync.SyncManager
import app.otakureader.domain.sync.SyncProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

/**
 * DI module for sync functionality.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    /**
     * Provides the set of available sync providers.
     */
    @Provides
    @Singleton
    fun provideSyncProviders(
        selfHostedProvider: SelfHostedSyncProvider
    ): Set<SyncProvider> = setOf<SyncProvider>(selfHostedProvider)

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

    /**
     * Provides the API factory for self-hosted sync.
     *
     * Note: Although SelfHostedSyncApiFactory has @Singleton + @Inject constructor
     * and Hilt could auto-provide it, this explicit @Provides method ensures consistent
     * module structure and allows for future customization if needed.
     */
    @Provides
    @Singleton
    fun provideSelfHostedSyncApiFactory(
        syncPreferences: SyncPreferences,
        okHttpClient: OkHttpClient,
        json: Json
    ): SelfHostedSyncApiFactory = SelfHostedSyncApiFactory(syncPreferences, okHttpClient, json)
}
