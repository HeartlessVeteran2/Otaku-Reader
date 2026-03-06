package app.komikku.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "komikku_prefs")

@Singleton
class PreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val READER_MODE = stringPreferencesKey("reader_mode")
        val GRID_SIZE = intPreferencesKey("grid_size")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val UPDATE_INTERVAL = intPreferencesKey("update_interval")
    }

    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    val dynamicColors: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLORS] ?: true }
    val readerMode: Flow<String> = context.dataStore.data.map { it[Keys.READER_MODE] ?: "rtl" }
    val gridSize: Flow<Int> = context.dataStore.data.map { it[Keys.GRID_SIZE] ?: 3 }
    val autoUpdateEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_UPDATE_ENABLED] ?: true }
    val updateInterval: Flow<Int> = context.dataStore.data.map { it[Keys.UPDATE_INTERVAL] ?: 12 }

    suspend fun setTheme(theme: String) { context.dataStore.edit { it[Keys.THEME] = theme } }
    suspend fun setDynamicColors(enabled: Boolean) { context.dataStore.edit { it[Keys.DYNAMIC_COLORS] = enabled } }
    suspend fun setReaderMode(mode: String) { context.dataStore.edit { it[Keys.READER_MODE] = mode } }
    suspend fun setGridSize(size: Int) { context.dataStore.edit { it[Keys.GRID_SIZE] = size } }
    suspend fun setAutoUpdateEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.AUTO_UPDATE_ENABLED] = enabled } }
    suspend fun setUpdateInterval(hours: Int) { context.dataStore.edit { it[Keys.UPDATE_INTERVAL] = hours } }
}
