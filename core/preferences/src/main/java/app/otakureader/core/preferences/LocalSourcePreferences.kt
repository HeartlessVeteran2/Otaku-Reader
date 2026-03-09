package app.otakureader.core.preferences

import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Preference store for the Local manga source settings.
 * Exposes reactive [Flow] properties and suspend setter functions backed by DataStore.
 */
open class LocalSourcePreferences(private val dataStore: DataStore<Preferences>) {

    /**
     * Absolute path of the directory that the Local source scans for manga.
     * Defaults to `<external storage>/OtakuReader/local` resolved via
     * [Environment.getExternalStorageDirectory] at runtime.
     */
    open val localSourceDirectory: Flow<String> =
        dataStore.data.map { it[Keys.LOCAL_SOURCE_DIRECTORY] ?: defaultDirectory() }

    open suspend fun setLocalSourceDirectory(path: String) {
        dataStore.edit { it[Keys.LOCAL_SOURCE_DIRECTORY] = path }
    }

    private object Keys {
        val LOCAL_SOURCE_DIRECTORY = stringPreferencesKey("local_source_directory")
    }

    companion object {
        /**
         * Returns the default scan directory path resolved at runtime via
         * [Environment.getExternalStorageDirectory] so the path is always correct
         * regardless of device-specific symlinks.
         */
        fun defaultDirectory(): String =
            "${Environment.getExternalStorageDirectory().absolutePath}/OtakuReader/local"

        /**
         * Create a [LocalSourcePreferences] that always returns [directory] for
         * [localSourceDirectory].  Intended for tests and standalone utilities that
         * do not have a proper DataStore instance available.
         */
        fun ofDirectory(directory: String): LocalSourcePreferences =
            object : LocalSourcePreferences(NoOpDataStore) {
                override val localSourceDirectory: Flow<String> = flowOf(directory)
                override suspend fun setLocalSourceDirectory(path: String) = Unit
            }

        /** No-op DataStore used by [ofDirectory] — its data is never actually read. */
        private val NoOpDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences
            ): Preferences = emptyPreferences()
        }
    }
}

