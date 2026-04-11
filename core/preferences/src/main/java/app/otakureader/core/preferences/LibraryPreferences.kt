package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    // --- NSFW Filter ---

    /** Whether to show NSFW content in the library. false = hide NSFW categories. */
    val showNsfwContent: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_NSFW_CONTENT] ?: false }
    suspend fun setShowNsfwContent(value: Boolean) = dataStore.edit { it[Keys.SHOW_NSFW_CONTENT] = value }

    /** Whether to show hidden categories in the library. */
    val showHiddenCategories: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_HIDDEN_CATEGORIES] ?: false }
    suspend fun setShowHiddenCategories(value: Boolean) = dataStore.edit { it[Keys.SHOW_HIDDEN_CATEGORIES] = value }

    // --- Library Update Settings ---

    /** Only update library when on Wi-Fi */
    val updateOnlyOnWifi: Flow<Boolean> = dataStore.data.map { it[Keys.UPDATE_ONLY_ON_WIFI] ?: false }
    suspend fun setUpdateOnlyOnWifi(value: Boolean) = dataStore.edit { it[Keys.UPDATE_ONLY_ON_WIFI] = value }

    /** Only update pinned categories */
    val updateOnlyPinnedCategories: Flow<Boolean> = dataStore.data.map { it[Keys.UPDATE_ONLY_PINNED_CATEGORIES] ?: false }
    suspend fun setUpdateOnlyPinnedCategories(value: Boolean) = dataStore.edit { it[Keys.UPDATE_ONLY_PINNED_CATEGORIES] = value }

    /** Skip updating categories with these IDs */
    val skipUpdateCategoryIds: Flow<Set<String>> = dataStore.data.map { it[Keys.SKIP_UPDATE_CATEGORY_IDS] ?: emptySet() }
    suspend fun setSkipUpdateCategoryIds(value: Set<String>) = dataStore.edit { it[Keys.SKIP_UPDATE_CATEGORY_IDS] = value }

    /** Category to jump to when opening library (null = default/first) */
    val jumpToCategoryOnOpen: Flow<Int?> = dataStore.data.map { it[Keys.JUMP_TO_CATEGORY_ON_OPEN] }
    suspend fun setJumpToCategoryOnOpen(value: Int?) = dataStore.edit {
        if (value != null) it[Keys.JUMP_TO_CATEGORY_ON_OPEN] = value else it.remove(Keys.JUMP_TO_CATEGORY_ON_OPEN)
    }

    /** Auto-refresh library on app start */
    val autoRefreshOnStart: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_REFRESH_ON_START] ?: false }
    suspend fun setAutoRefreshOnStart(value: Boolean) = dataStore.edit { it[Keys.AUTO_REFRESH_ON_START] = value }

    /** Show update progress notification */
    val showUpdateProgress: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_UPDATE_PROGRESS] ?: true }
    suspend fun setShowUpdateProgress(value: Boolean) = dataStore.edit { it[Keys.SHOW_UPDATE_PROGRESS] = value }

    private object Keys {
        val GRID_SIZE = intPreferencesKey("library_grid_size")
        val SHOW_BADGES = booleanPreferencesKey("library_show_badges")
        val LIBRARY_SORT_MODE = intPreferencesKey("library_sort_mode")
        val LIBRARY_DISPLAY_MODE = intPreferencesKey("library_display_mode")
        val LIBRARY_FILTER_MODE = intPreferencesKey("library_filter_mode")
        val LIBRARY_FILTER_SOURCE = longPreferencesKey("library_filter_source")
        val SHOW_NSFW_CONTENT = booleanPreferencesKey("library_show_nsfw_content")
        val SHOW_HIDDEN_CATEGORIES = booleanPreferencesKey("library_show_hidden_categories")
        val UPDATE_ONLY_ON_WIFI = booleanPreferencesKey("library_update_only_on_wifi")
        val UPDATE_ONLY_PINNED_CATEGORIES = booleanPreferencesKey("library_update_only_pinned_categories")
        val SKIP_UPDATE_CATEGORY_IDS = stringSetPreferencesKey("library_skip_update_category_ids")
        val JUMP_TO_CATEGORY_ON_OPEN = intPreferencesKey("library_jump_to_category_on_open")
        val AUTO_REFRESH_ON_START = booleanPreferencesKey("library_auto_refresh_on_start")
        val SHOW_UPDATE_PROGRESS = booleanPreferencesKey("library_show_update_progress")
    }
}
