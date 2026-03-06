package app.komikku.core.network

import kotlinx.serialization.Serializable

@Serializable
data class NetworkConfig(
    val baseUrl: String = "",
    val timeoutSeconds: Int = 30
)

interface NetworkClient {
    suspend fun connect(config: NetworkConfig)
}
