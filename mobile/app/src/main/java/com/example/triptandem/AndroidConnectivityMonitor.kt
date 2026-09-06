package com.triptandem

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.triptandem.shared.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reports whether Android currently has an internet-capable network. */
internal class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {
    private val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(currentlyOnline())
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = currentlyOnline()
        }

        override fun onLost(network: Network) {
            _isOnline.value = currentlyOnline()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isOnline.value = currentlyOnline()
        }
    }

    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onFailure { _isOnline.value = true }
    }

    override fun close() {
        runCatching { manager.unregisterNetworkCallback(callback) }
    }

    private fun currentlyOnline(): Boolean = manager.activeNetwork
        ?.let(manager::getNetworkCapabilities)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
