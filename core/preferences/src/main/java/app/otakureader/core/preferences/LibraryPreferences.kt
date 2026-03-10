package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for library-related settings including grid size and badges.
 * Exposes reactive [Flow] properties and suspend setter functions backed by DataStore.
 */
class LibraryPreferences(private val dataStore: DataStore<Preferences>) {

    // --- Grid ---

    /** Number of columns in the library grid (2–5). */
    val gridSize: Flow<Int> = dataStore.data.map { it[Keys.GRID_SIZE] ?: 3 }
    suspend fun setGridSize(value: Int) = dataStore.edit { it[Keys.GRID_SIZE] = value }

    // --- Badges ---

    /** Whether to show unread-count badges on library covers. */
    val showBadges: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_BADGES] ?: true }
    suspend fun setShowBadges(value: Boolean) = dataStore.edit { it[Keys.SHOW_BADGES] = value }

    // --- Sort and Display ---

    val librarySortMode: Flow<Int> = dataStore.data.map { it[Keys.LIBRARY_SORT_MODE] ?: 0 }
    suspend fun setLibrarySortMode(value: Int) = dataStore.edit { it[Keys.LIBRARY_SORT_MODE] = value }

    val libraryDisplayMode: Flow<Int> = dataStore.data.map { it[Keys.LIBRARY_DISPLAY_MODE] ?: 0 }
    suspend fun setLibraryDisplayMode(value: Int) = dataStore.edit { it[Keys.LIBRARY_DISPLAY_MODE] = value }

    // --- Filters ---

    /** Filter mode: 0=ALL, 1=DOWNLOADED, 2=UNREAD, 3=COMPLETED, 4=TRACKING */
    val libraryFilterMode: Flow<Int> = dataStore.data.map { it[Keys.LIBRARY_FILTER_MODE] ?: 0 }
    suspend fun setLibraryFilterMode(value: Int) = dataStore.edit { it[Keys.LIBRARY_FILTER_MODE] = value }

    /** Filter by source ID. null = all sources */
    val libraryFilterSourceId: Flow<Long?> = dataStore.data.map { it[Keys.LIBRARY_FILTER_SOURCE] }
    suspend fun setLibraryFilterSourceId(value: Long?) = dataStore.edit { 
        if (value != null) it[Keys.LIBRARY_FILTER_SOURCE] = value else it.remove(Keys.LIBRARY_FILTER_SOURCE)
    }

    private object Keys {
        val GRID_SIZE = intPreferencesKey("library_grid_size")
        val SHOW_BADGES = booleanPreferencesKey("library_show_badges")
        val LIBRARY_SORT_MODE = intPreferencesKey("library_sort_mode")
        val LIBRARY_DISPLAY_MODE = intPreferencesKey("library_display_mode")
        val LIBRARY_FILTER_MODE = intPreferencesKey("library_filter_mode")
        val LIBRARY_FILTER_SOURCE = longPreferencesKey("library_filter_source")
    }
}
