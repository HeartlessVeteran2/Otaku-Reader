package app.otakureader.core.extension.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Implementation of ExtensionRepoRepository using DataStore.
 * Stores repository URLs as a set and tracks the active repository.
 */
class ExtensionRepoRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : ExtensionRepoRepository {

    companion object {
        // Default repository URLs
        const val DEFAULT_KEIYOUSHI_REPO = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
        const val DEFAULT_KOMIKKU_REPO = "https://raw.githubusercontent.com/komikku-app/extensions/repo"

        private val REPOSITORIES_KEY = stringSetPreferencesKey("extension_repositories")
        private val ACTIVE_REPOSITORY_KEY = stringPreferencesKey("active_extension_repository")
    }

    override fun getRepositories(): Flow<List<String>> {
        return dataStore.data.map { preferences ->
            preferences[REPOSITORIES_KEY]?.toList()
                ?: listOf(DEFAULT_KEIYOUSHI_REPO, DEFAULT_KOMIKKU_REPO)
        }
    }

    override suspend fun addRepository(url: String) {
        dataStore.edit { preferences ->
            val currentRepos = preferences[REPOSITORIES_KEY]?.toMutableSet() ?: mutableSetOf()
            currentRepos.add(url)
            preferences[REPOSITORIES_KEY] = currentRepos

            // If this is the first repository, set it as active
            if (!preferences.contains(ACTIVE_REPOSITORY_KEY)) {
                preferences[ACTIVE_REPOSITORY_KEY] = url
            }
        }
    }

    override suspend fun removeRepository(url: String) {
        dataStore.edit { preferences ->
            val currentRepos = preferences[REPOSITORIES_KEY]?.toMutableSet() ?: mutableSetOf()
            currentRepos.remove(url)
            preferences[REPOSITORIES_KEY] = currentRepos

            // If removing the active repository, set a new one
            if (preferences[ACTIVE_REPOSITORY_KEY] == url) {
                preferences[ACTIVE_REPOSITORY_KEY] = currentRepos.firstOrNull() ?: DEFAULT_KEIYOUSHI_REPO
            }
        }
    }

    override suspend fun getActiveRepository(): String {
        return dataStore.data.first()[ACTIVE_REPOSITORY_KEY] ?: DEFAULT_KEIYOUSHI_REPO
    }

    override suspend fun setActiveRepository(url: String) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_REPOSITORY_KEY] = url

            // Ensure the URL is in the repositories list
            val currentRepos = preferences[REPOSITORIES_KEY]?.toMutableSet() ?: mutableSetOf()
            currentRepos.add(url)
            preferences[REPOSITORIES_KEY] = currentRepos
        }
    }

    override suspend fun clearRepositories() {
        dataStore.edit { preferences ->
            preferences.remove(REPOSITORIES_KEY)
            preferences[ACTIVE_REPOSITORY_KEY] = DEFAULT_KEIYOUSHI_REPO
        }
    }
}
