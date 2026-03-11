package app.otakureader.core.preferences.di

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.otakureader.core.preferences.AiPreferences
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.core.preferences.BackupPreferences
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.EncryptedApiKeyStore
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.core.preferences.SyncPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val DATASTORE_FILE_NAME = "otakureader_prefs"

// Legacy key for the library-scoped NSFW setting that was merged into GeneralPreferences.
private val LEGACY_LIBRARY_SHOW_NSFW = booleanPreferencesKey("library_show_nsfw")
// Current global NSFW key owned by GeneralPreferences.
private val GENERAL_SHOW_NSFW_CONTENT = booleanPreferencesKey("show_nsfw_content")

// Legacy DataStore key for the Gemini API key that was moved to EncryptedSharedPreferences.
private val LEGACY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")

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

/**
 * One-time DataStore migration: copies any existing plaintext `gemini_api_key` value from
 * DataStore into [EncryptedApiKeyStore] and removes the plaintext key.
 *
 * This ensures that users who had the key stored before encrypted storage was introduced
 * don't lose their setting, and that the plaintext copy is removed from disk.
 */
private class GeminiApiKeyMigration(
    private val encryptedApiKeyStore: EncryptedApiKeyStore
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[LEGACY_GEMINI_API_KEY] != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacyKey = currentData[LEGACY_GEMINI_API_KEY]
        if (!legacyKey.isNullOrEmpty()) {
            // Write the legacy plaintext value into secure encrypted storage.
            encryptedApiKeyStore.setGeminiApiKey(legacyKey)
        }
        return currentData.toMutablePreferences().apply {
            remove(LEGACY_GEMINI_API_KEY)
        }.toPreferences()
    }

    override suspend fun cleanUp() {
        // No additional cleanup needed; the legacy key is removed in migrate().
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideEncryptedApiKeyStore(@ApplicationContext context: Context): EncryptedApiKeyStore =
        EncryptedApiKeyStore(context)

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
        encryptedApiKeyStore: EncryptedApiKeyStore
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(
            LibraryNsfwToGeneralMigration,
            GeminiApiKeyMigration(encryptedApiKeyStore)
        ),
        produceFile = { context.preferencesDataStoreFile(DATASTORE_FILE_NAME) }
    )

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
        encryptedApiKeyStore: EncryptedApiKeyStore
    ): AiPreferences = AiPreferences(dataStore, encryptedApiKeyStore)
}
