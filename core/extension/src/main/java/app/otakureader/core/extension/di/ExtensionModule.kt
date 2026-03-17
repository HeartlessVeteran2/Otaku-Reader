package app.otakureader.core.extension.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import app.otakureader.core.extension.BuildConfig
import app.otakureader.core.extension.data.local.ExtensionDao
import app.otakureader.core.extension.data.local.ExtensionDatabase
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSource
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSourceImpl
import app.otakureader.core.extension.data.repository.ExtensionRepoRepositoryImpl
import app.otakureader.core.extension.data.repository.ExtensionRepositoryImpl
import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.installer.ExtensionInstaller
import app.otakureader.core.extension.loader.ExtensionLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
        repoRepository: ExtensionRepoRepository
    ): ExtensionRemoteDataSource {
        return ExtensionRemoteDataSourceImpl(repoRepository)
    }

    @Provides
    @Singleton
    fun provideExtensionRepository(
        dao: ExtensionDao,
        remoteDataSource: ExtensionRemoteDataSource
    ): ExtensionRepository {
        return ExtensionRepositoryImpl(dao, remoteDataSource)
    }

    @Provides
    @Singleton
    fun provideExtensionLoader(
        @ApplicationContext context: Context
    ): ExtensionLoader {
        return ExtensionLoader(context)
    }

    @Provides
    @Singleton
    fun provideExtensionInstaller(
        @ApplicationContext context: Context,
        repository: ExtensionRepository,
        loader: ExtensionLoader,
        remoteDataSource: ExtensionRemoteDataSource
    ): ExtensionInstaller {
        return ExtensionInstaller(context, repository, loader, remoteDataSource)
    }
}
