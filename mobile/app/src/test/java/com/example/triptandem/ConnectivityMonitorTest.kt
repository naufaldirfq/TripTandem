package com.example.triptandem

import com.triptandem.shared.MutableConnectivityMonitor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityMonitorTest {
    @Test
    fun nativeConnectivityBridge_updatesSharedState() {
        val monitor = MutableConnectivityMonitor(initialOnline = true)

        assertTrue(monitor.isOnline.value)
        monitor.setOnline(false)
        assertFalse(monitor.isOnline.value)
        monitor.setOnline(true)
        assertTrue(monitor.isOnline.value)
    }
}
