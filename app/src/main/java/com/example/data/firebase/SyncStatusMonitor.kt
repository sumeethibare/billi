package com.example.data.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.model.SyncStatusUi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Monitors real-time network reachability via Android ConnectivityManager and combines it
 * with Firestore SnapshotMetadata pending-write tracking so Billi can display live
 * Online / Syncing / Offline-Queued indicators and transition toasts.
 */
class SyncStatusMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _pendingWritesMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _isFromCache = MutableStateFlow(false)

    val isOnlineFlow: Flow<Boolean> = callbackFlow {
        fun checkOnline(): Boolean {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        trySend(checkOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(checkOnline())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(validated || checkOnline())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            trySend(checkOnline())
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()

    fun isCurrentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun reportPendingWrites(sourceKey: String, pendingCount: Int, fromCache: Boolean) {
        val updated = _pendingWritesMap.value.toMutableMap()
        if (pendingCount <= 0) {
            updated.remove(sourceKey)
        } else {
            updated[sourceKey] = pendingCount
        }
        _pendingWritesMap.value = updated
        _isFromCache.value = fromCache
    }

    val syncStatusFlow: Flow<SyncStatusUi> = combine(
        isOnlineFlow,
        _pendingWritesMap,
        _isFromCache
    ) { online, pendingMap, fromCache ->
        SyncStatusUi(
            isOnline = online,
            pendingWritesCount = pendingMap.values.sum(),
            isFromCache = fromCache
        )
    }.distinctUntilChanged()
}
