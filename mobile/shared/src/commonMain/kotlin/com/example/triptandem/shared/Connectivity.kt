package com.triptandem.shared

import kotlinx.coroutines.flow.StateFlow

/**
 * Small platform-neutral connectivity boundary used by server-backed flows.
 * A conservative default keeps previews and tests usable when no host monitor
 * is injected; production hosts provide a real network callback/monitor.
 */
interface ConnectivityMonitor {
    val isOnline: StateFlow<Boolean>

    /** Releases platform callbacks when the Compose host is disposed. */
    fun close()
}

/** Mutable bridge for native hosts whose connectivity callback lives in Swift. */
class MutableConnectivityMonitor(initialOnline: Boolean = true) : ConnectivityMonitor {
    private val _isOnline = kotlinx.coroutines.flow.MutableStateFlow(initialOnline)

    override val isOnline: StateFlow<Boolean> = _isOnline

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }

    override fun close() = Unit
}

object AlwaysOnlineConnectivityMonitor : ConnectivityMonitor {
    private val online = kotlinx.coroutines.flow.MutableStateFlow(true)

    override val isOnline: StateFlow<Boolean> = online

    override fun close() = Unit
}
