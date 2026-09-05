// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkStateMonitorTest {

    @Test
    fun normalizeSsid_strips_quotes() {
        assertEquals("MyWiFi", NetworkStateMonitor.normalizeSsid("\"MyWiFi\""))
        assertEquals("Coffee Shop 2.4", NetworkStateMonitor.normalizeSsid("\"Coffee Shop 2.4\""))
        assertEquals("AlreadyUnquoted", NetworkStateMonitor.normalizeSsid("AlreadyUnquoted"))
    }

    @Test
    fun normalizeSsid_handles_unknown_and_empty() {
        assertNull(NetworkStateMonitor.normalizeSsid(null))
        assertNull(NetworkStateMonitor.normalizeSsid(""))
        assertNull(NetworkStateMonitor.normalizeSsid("\"\""))
        assertNull(NetworkStateMonitor.normalizeSsid("   "))
        assertNull(NetworkStateMonitor.normalizeSsid("<unknown ssid>"))
        assertNull(NetworkStateMonitor.normalizeSsid("\"<unknown ssid>\""))
    }
}
