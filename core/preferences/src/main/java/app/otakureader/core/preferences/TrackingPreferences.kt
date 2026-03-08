package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for tracking service authentication and settings.
 * Stores OAuth tokens, user IDs, and tracking preferences for MAL, AniList, and Kitsu.
 */
class TrackingPreferences(private val dataStore: DataStore<Preferences>) {

    // --- MyAnimeList ---

    val malAccessToken: Flow<String?> = dataStore.data.map { it[Keys.MAL_ACCESS_TOKEN] }
    suspend fun setMalAccessToken(value: String?) = dataStore.edit {
        if (value != null) it[Keys.MAL_ACCESS_TOKEN] = value
        else it.remove(Keys.MAL_ACCESS_TOKEN)
    }

    val malRefreshToken: Flow<String?> = dataStore.data.map { it[Keys.MAL_REFRESH_TOKEN] }
    suspend fun setMalRefreshToken(value: String?) = dataStore.edit {
        if (value != null) it[Keys.MAL_REFRESH_TOKEN] = value
        else it.remove(Keys.MAL_REFRESH_TOKEN)
    }

    val malTokenExpiry: Flow<Long> = dataStore.data.map { it[Keys.MAL_TOKEN_EXPIRY] ?: 0L }
    suspend fun setMalTokenExpiry(value: Long) = dataStore.edit { it[Keys.MAL_TOKEN_EXPIRY] = value }

    val malUsername: Flow<String?> = dataStore.data.map { it[Keys.MAL_USERNAME] }
    suspend fun setMalUsername(value: String?) = dataStore.edit {
        if (value != null) it[Keys.MAL_USERNAME] = value
        else it.remove(Keys.MAL_USERNAME)
    }

    // --- AniList ---

    val anilistAccessToken: Flow<String?> = dataStore.data.map { it[Keys.ANILIST_ACCESS_TOKEN] }
    suspend fun setAnilistAccessToken(value: String?) = dataStore.edit {
        if (value != null) it[Keys.ANILIST_ACCESS_TOKEN] = value
        else it.remove(Keys.ANILIST_ACCESS_TOKEN)
    }

    val anilistTokenExpiry: Flow<Long> = dataStore.data.map { it[Keys.ANILIST_TOKEN_EXPIRY] ?: 0L }
    suspend fun setAnilistTokenExpiry(value: Long) = dataStore.edit { it[Keys.ANILIST_TOKEN_EXPIRY] = value }

    val anilistUserId: Flow<Long> = dataStore.data.map { it[Keys.ANILIST_USER_ID] ?: 0L }
    suspend fun setAnilistUserId(value: Long) = dataStore.edit { it[Keys.ANILIST_USER_ID] = value }

    val anilistUsername: Flow<String?> = dataStore.data.map { it[Keys.ANILIST_USERNAME] }
    suspend fun setAnilistUsername(value: String?) = dataStore.edit {
        if (value != null) it[Keys.ANILIST_USERNAME] = value
        else it.remove(Keys.ANILIST_USERNAME)
    }

    // --- Kitsu ---

    val kitsuAccessToken: Flow<String?> = dataStore.data.map { it[Keys.KITSU_ACCESS_TOKEN] }
    suspend fun setKitsuAccessToken(value: String?) = dataStore.edit {
        if (value != null) it[Keys.KITSU_ACCESS_TOKEN] = value
        else it.remove(Keys.KITSU_ACCESS_TOKEN)
    }

    val kitsuRefreshToken: Flow<String?> = dataStore.data.map { it[Keys.KITSU_REFRESH_TOKEN] }
    suspend fun setKitsuRefreshToken(value: String?) = dataStore.edit {
        if (value != null) it[Keys.KITSU_REFRESH_TOKEN] = value
        else it.remove(Keys.KITSU_REFRESH_TOKEN)
    }

    val kitsuUserId: Flow<Long> = dataStore.data.map { it[Keys.KITSU_USER_ID] ?: 0L }
    suspend fun setKitsuUserId(value: Long) = dataStore.edit { it[Keys.KITSU_USER_ID] = value }

    val kitsuUsername: Flow<String?> = dataStore.data.map { it[Keys.KITSU_USERNAME] }
    suspend fun setKitsuUsername(value: String?) = dataStore.edit {
        if (value != null) it[Keys.KITSU_USERNAME] = value
        else it.remove(Keys.KITSU_USERNAME)
    }

    // --- Auto-sync Settings ---

    val autoSyncEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_SYNC_ENABLED] ?: true }
    suspend fun setAutoSyncEnabled(value: Boolean) = dataStore.edit { it[Keys.AUTO_SYNC_ENABLED] = value }

    // --- Logout helpers ---

    suspend fun clearMalAuth() {
        dataStore.edit {
            it.remove(Keys.MAL_ACCESS_TOKEN)
            it.remove(Keys.MAL_REFRESH_TOKEN)
            it.remove(Keys.MAL_TOKEN_EXPIRY)
            it.remove(Keys.MAL_USERNAME)
        }
    }

    suspend fun clearAnilistAuth() {
        dataStore.edit {
            it.remove(Keys.ANILIST_ACCESS_TOKEN)
            it.remove(Keys.ANILIST_TOKEN_EXPIRY)
            it.remove(Keys.ANILIST_USER_ID)
            it.remove(Keys.ANILIST_USERNAME)
        }
    }

    suspend fun clearKitsuAuth() {
        dataStore.edit {
            it.remove(Keys.KITSU_ACCESS_TOKEN)
            it.remove(Keys.KITSU_REFRESH_TOKEN)
            it.remove(Keys.KITSU_USER_ID)
            it.remove(Keys.KITSU_USERNAME)
        }
    }

    private object Keys {
        // MyAnimeList
        val MAL_ACCESS_TOKEN = stringPreferencesKey("mal_access_token")
        val MAL_REFRESH_TOKEN = stringPreferencesKey("mal_refresh_token")
        val MAL_TOKEN_EXPIRY = longPreferencesKey("mal_token_expiry")
        val MAL_USERNAME = stringPreferencesKey("mal_username")

        // AniList
        val ANILIST_ACCESS_TOKEN = stringPreferencesKey("anilist_access_token")
        val ANILIST_TOKEN_EXPIRY = longPreferencesKey("anilist_token_expiry")
        val ANILIST_USER_ID = longPreferencesKey("anilist_user_id")
        val ANILIST_USERNAME = stringPreferencesKey("anilist_username")

        // Kitsu
        val KITSU_ACCESS_TOKEN = stringPreferencesKey("kitsu_access_token")
        val KITSU_REFRESH_TOKEN = stringPreferencesKey("kitsu_refresh_token")
        val KITSU_USER_ID = longPreferencesKey("kitsu_user_id")
        val KITSU_USERNAME = stringPreferencesKey("kitsu_username")

        // Settings
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
    }
}
