package app.otakureader.core.common.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkType { WIFI, MOBILE, OFFLINE }

@Singleton
class NetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun currentNetwork(): NetworkType {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return NetworkType.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            else -> NetworkType.OFFLINE
        }
    }

    fun networkFlow(): Flow<NetworkType> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(currentNetwork())
            }
            override fun onLost(network: Network) { trySend(currentNetwork()) }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        trySend(currentNetwork())
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun isUnmetered(): Boolean {
        connectivityManager.activeNetwork ?: return false
        return !connectivityManager.isActiveNetworkMetered
    }
}
