package app.otakureader.core.preferences.di

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.otakureader.core.preferences.AiPreferences
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.core.preferences.BackupPreferences
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.EncryptedOpdsCredentialStore
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.core.preferences.SyncPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Legacy key for the library-scoped NSFW setting that was merged into GeneralPreferences.
private val LEGACY_LIBRARY_SHOW_NSFW = booleanPreferencesKey("library_show_nsfw")
// Current global NSFW key owned by GeneralPreferences.
private val GENERAL_SHOW_NSFW_CONTENT = booleanPreferencesKey("show_nsfw_content")

/**
 * One-time DataStore migration: copies any existing `library_show_nsfw` value into
 * `show_nsfw_content` (if not already set) and removes the legacy key.
 *
 * This avoids resetting users' NSFW preference when upgrading from the version that
 * introduced the now-removed library-specific NSFW toggle.
 */
private object LibraryNsfwToGeneralMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[LEGACY_LIBRARY_SHOW_NSFW] != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        return currentData.toMutablePreferences().apply {
            val legacyValue = currentData[LEGACY_LIBRARY_SHOW_NSFW] ?: return@apply
            // Only propagate if the global key has not been explicitly set yet.
            if (this[GENERAL_SHOW_NSFW_CONTENT] == null) {
                this[GENERAL_SHOW_NSFW_CONTENT] = legacyValue
            }
            remove(LEGACY_LIBRARY_SHOW_NSFW)
        }.toPreferences()
    }

    override suspend fun cleanUp() {
        // No additional cleanup needed; the legacy key is removed in migrate().
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "otakureader_prefs",
    produceMigrations = { _ -> listOf(LibraryNsfwToGeneralMigration) }
)

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideAppPreferences(dataStore: DataStore<Preferences>): AppPreferences =
        AppPreferences(dataStore)

    @Provides
    @Singleton
    fun provideGeneralPreferences(dataStore: DataStore<Preferences>): GeneralPreferences =
        GeneralPreferences(dataStore)

    @Provides
    @Singleton
    fun provideLibraryPreferences(dataStore: DataStore<Preferences>): LibraryPreferences =
        LibraryPreferences(dataStore)

    @Provides
    @Singleton
    fun provideReaderPreferences(dataStore: DataStore<Preferences>): ReaderPreferences =
        ReaderPreferences(dataStore)

    @Provides
    @Singleton
    fun provideDownloadPreferences(dataStore: DataStore<Preferences>): DownloadPreferences =
        DownloadPreferences(dataStore)

    @Provides
    @Singleton
    fun provideLocalSourcePreferences(dataStore: DataStore<Preferences>): LocalSourcePreferences =
        LocalSourcePreferences(dataStore)

    @Provides
    @Singleton
    fun provideBackupPreferences(dataStore: DataStore<Preferences>): BackupPreferences =
        BackupPreferences(dataStore)

    @Provides
    @Singleton
    fun provideSyncPreferences(dataStore: DataStore<Preferences>): SyncPreferences =
        SyncPreferences(dataStore)

    @Provides
    @Singleton
    fun provideAiPreferences(
        dataStore: DataStore<Preferences>,
        @ApplicationContext context: Context
    ): AiPreferences = AiPreferences(dataStore, context)

    @Provides
    @Singleton
    fun provideReadingGoalPreferences(dataStore: DataStore<Preferences>): ReadingGoalPreferences =
        ReadingGoalPreferences(dataStore)

    @Provides
    @Singleton
    fun provideEncryptedOpdsCredentialStore(
        @ApplicationContext context: Context
    ): EncryptedOpdsCredentialStore = EncryptedOpdsCredentialStore(context)
}
