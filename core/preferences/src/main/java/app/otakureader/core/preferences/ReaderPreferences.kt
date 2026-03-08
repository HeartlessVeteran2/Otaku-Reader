package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for reader-related settings such as reading mode, keep-screen-on, and scale.
 * Exposes reactive [Flow] properties and suspend setter functions backed by DataStore.
 */
class ReaderPreferences(private val dataStore: DataStore<Preferences>) {

    // --- Reading Mode ---

    /** Reader display mode ordinal — matches [app.otakureader.feature.reader.model.ReaderMode]:
     *  0 = SINGLE_PAGE, 1 = DUAL_PAGE, 2 = WEBTOON, 3 = SMART_PANELS. */
    val readerMode: Flow<Int> = dataStore.data.map { it[Keys.READER_MODE] ?: 0 }
    suspend fun setReaderMode(value: Int) = dataStore.edit { it[Keys.READER_MODE] = value }

    // --- Screen ---

    val keepScreenOn: Flow<Boolean> = dataStore.data.map { it[Keys.KEEP_SCREEN_ON] ?: true }
    suspend fun setKeepScreenOn(value: Boolean) = dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }

    // --- Scale ---

    val readerScale: Flow<Int> = dataStore.data.map { it[Keys.READER_SCALE] ?: 0 }
    suspend fun setReaderScale(value: Int) = dataStore.edit { it[Keys.READER_SCALE] = value }

    private object Keys {
        val READER_MODE = intPreferencesKey("reader_mode_setting")
        val KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val READER_SCALE = intPreferencesKey("reader_scale")
    }
}
