package app.komikku.data

import app.komikku.core.network.NetworkClient
import app.komikku.core.preferences.PreferencesStore
import app.komikku.domain.model.Manga
import app.komikku.source.api.Source

class MangaRepository(
    private val networkClient: NetworkClient,
    private val source: Source,
    private val preferencesStore: PreferencesStore,
) {
    suspend fun refreshLibrary(): List<Manga> {
        // Placeholder implementation
        networkClient.connect(config = app.komikku.core.network.NetworkConfig())
        return source.search(preferencesStore.host)
    }
}
