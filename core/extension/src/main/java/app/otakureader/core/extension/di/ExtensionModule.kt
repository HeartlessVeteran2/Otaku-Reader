package app.otakureader.core.extension.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.otakureader.core.extension.blocklist.ExtensionBlocklistFetcher
import app.otakureader.core.extension.blocklist.ExtensionBlocklistStore
import app.otakureader.core.extension.BuildConfig
import app.otakureader.core.extension.data.local.ExtensionDao
import app.otakureader.core.extension.data.local.ExtensionDatabase
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSource
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSourceImpl
import app.otakureader.core.extension.data.repository.ExtensionRepoRepositoryImpl
import app.otakureader.core.extension.data.repository.ExtensionRepositoryImpl
import app.otakureader.core.extension.domain.backend.JsExtensionBackend
import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.installer.ExtensionInstaller
import app.otakureader.core.extension.loader.ExtensionLoader
import app.otakureader.core.extension.loader.TrustedSignatureStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** v3 -> v4: repository provenance column (#1019). */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE extensions ADD COLUMN source_repo_url TEXT DEFAULT NULL")
    }
}

/**
 * v4 -> v5: JavaScript-backend discriminator.
 *
 * `NOT NULL DEFAULT 0` rather than a nullable column: every row that exists before this
 * migration is an APK extension, and that is a fact, not a missing value. A nullable column
 * would push a three-state question ("APK, JavaScript, or unknown?") onto every read of a
 * flag that only ever has two answers.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE extensions ADD COLUMN is_javascript INTEGER NOT NULL DEFAULT 0")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ExtensionModule {

    @Provides
    @Singleton
    fun provideExtensionDatabase(
        @ApplicationContext context: Context
    ): ExtensionDatabase {
        val builder = Room.databaseBuilder(
            context,
            ExtensionDatabase::class.java,
            "extension_database"
        )
        builder.addMigrations(MIGRATION_3_4, MIGRATION_4_5)
        // Only allow destructive migration in debug builds to avoid silently wiping
        // extension metadata in production if a migration is missing.
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration(dropAllTables = true)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideExtensionDao(database: ExtensionDatabase): ExtensionDao {
        return database.extensionDao()
    }

    @Provides
    @Singleton
    fun provideExtensionRepoRepository(
        dataStore: DataStore<Preferences>
    ): ExtensionRepoRepository {
        return ExtensionRepoRepositoryImpl(dataStore)
    }

    @Provides
    @Singleton
    fun provideExtensionRemoteDataSource(
        repoRepository: ExtensionRepoRepository,
        jsBackend: JsExtensionBackend,
    ): ExtensionRemoteDataSource {
        return ExtensionRemoteDataSourceImpl(repoRepository, jsBackend = jsBackend)
    }

    @Provides
    @Singleton
    fun provideExtensionRepository(
        dao: ExtensionDao,
        remoteDataSource: ExtensionRemoteDataSource,
        loader: ExtensionLoader,
        blocklistStore: ExtensionBlocklistStore
    ): ExtensionRepository {
        return ExtensionRepositoryImpl(dao, remoteDataSource, loader, blocklistStore)
    }

    @Provides
    @Singleton
    fun provideExtensionBlocklistStore(
        dataStore: DataStore<Preferences>
    ): ExtensionBlocklistStore {
        return ExtensionBlocklistStore(dataStore)
    }

    @Provides
    @Singleton
    fun provideExtensionBlocklistFetcher(): ExtensionBlocklistFetcher {
        return ExtensionBlocklistFetcher()
    }

    @Provides
    @Singleton
    fun provideExtensionLoader(
        @ApplicationContext context: Context,
        trustedSignatureStore: TrustedSignatureStore
    ): ExtensionLoader {
        return ExtensionLoader(context, trustedSignatureStore)
    }

    @Provides
    @Singleton
    fun provideExtensionInstaller(
        @ApplicationContext context: Context,
        repository: ExtensionRepository,
        loader: ExtensionLoader,
        remoteDataSource: ExtensionRemoteDataSource,
        jsBackend: JsExtensionBackend,
    ): ExtensionInstaller {
        return ExtensionInstaller(context, repository, loader, remoteDataSource, jsBackend)
    }
}
