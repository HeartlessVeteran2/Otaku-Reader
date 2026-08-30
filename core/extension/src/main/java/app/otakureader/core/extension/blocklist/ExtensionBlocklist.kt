package app.otakureader.core.extension.blocklist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSourceImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.otakureader.core.common.net.await
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Remotely updateable extension blocklist (#1018).
 *
 * A known-bad extension can pass the signer-continuity check (it was signed by the
 * original key) yet still need revocation — e.g. the key leaked or the source turned
 * malicious. The blocklist is a signed-repo-hosted JSON document fetched daily by
 * [app.otakureader.data.worker — ExtensionBlocklistRefreshWorker] and cached in
 * DataStore so enforcement works offline. The canonical document lives at the
 * project repository root (`extension-blocklist.json`) and is served over HTTPS
 * from raw.githubusercontent.com; only project maintainers can change it.
 */
@Serializable
data class BlocklistDocument(
    val version: Int = 1,
    val blocked: List<BlockedExtension> = emptyList(),
)

@Serializable
data class BlockedExtension(
    val pkgName: String,
    val reason: String = "",
)

/** DataStore-backed cache of the last successfully fetched blocklist. */
class ExtensionBlocklistStore(private val dataStore: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Blocked package names mapped to the human-readable block reason. */
    val blockedPackages: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[Keys.BLOCKLIST_JSON]?.let { raw ->
            runCatching { json.decodeFromString<BlocklistDocument>(raw) }
                .getOrNull()
                ?.blocked
                ?.associate { it.pkgName to it.reason }
        } ?: emptyMap()
    }

    val lastFetchedAt: Flow<Long?> = dataStore.data.map { it[Keys.BLOCKLIST_FETCHED_AT] }

    suspend fun replace(document: BlocklistDocument) {
        dataStore.edit { prefs ->
            prefs[Keys.BLOCKLIST_JSON] = json.encodeToString(BlocklistDocument.serializer(), document)
            prefs[Keys.BLOCKLIST_FETCHED_AT] = System.currentTimeMillis()
        }
    }

    private object Keys {
        val BLOCKLIST_JSON = stringPreferencesKey("extension_blocklist_json")
        val BLOCKLIST_FETCHED_AT = longPreferencesKey("extension_blocklist_fetched_at")
    }
}

/** Fetches the blocklist document from the project-controlled HTTPS endpoint. */
class ExtensionBlocklistFetcher(
    private val httpClient: OkHttpClient = ExtensionRemoteDataSourceImpl.createDefaultClient(),
    private val blocklistUrl: String = DEFAULT_BLOCKLIST_URL,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(): Result<BlocklistDocument> = withContext(Dispatchers.IO) {
        runCatching {
            require(blocklistUrl.startsWith("https://")) { "Blocklist URL must use HTTPS" }
            val request = Request.Builder().url(blocklistUrl).build()
            httpClient.newCall(request).await().use { response ->
                check(response.isSuccessful) { "Blocklist fetch failed: HTTP ${response.code}" }
                val body = response.body?.string() ?: error("Empty blocklist response")
                json.decodeFromString<BlocklistDocument>(body)
            }
        }
    }

    companion object {
        /**
         * Canonical blocklist location — the file at the repository root on the default
         * branch. Served over HTTPS; writable only by repository maintainers.
         */
        const val DEFAULT_BLOCKLIST_URL =
            "https://raw.githubusercontent.com/Heartless-Veteran/Otaku-Reader/main/extension-blocklist.json"
    }
}
