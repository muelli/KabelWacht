// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelRunConditionsTest {

    @Test
    fun disabled_conditions_never_run() {
        val conditions = TunnelRunConditions(enabled = false)
        val wifi = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = "Home")
        assertFalse(conditions.shouldRun(wifi))
    }

    @Test
    fun disconnected_network_never_runs() {
        val conditions = TunnelRunConditions(enabled = true)
        val disconnected = NetworkStateSnapshot(isConnected = false)
        assertFalse(conditions.shouldRun(disconnected))
    }

    @Test
    fun only_on_wifi() {
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiEnabled = true,
            wifiMode = WifiConditionMode.ANY,
            mobileMode = MobileConditionMode.NEVER,
            ethernetEnabled = false,
        )

        val wifi = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = "Coffee")
        val mobile = NetworkStateSnapshot(isConnected = true, isMobile = true)
        val eth = NetworkStateSnapshot(isConnected = true, isEthernet = true)

        assertTrue(conditions.shouldRun(wifi))
        assertFalse(conditions.shouldRun(mobile))
        assertFalse(conditions.shouldRun(eth))
    }

    @Test
    fun wifi_except_home_wifi() {
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiEnabled = true,
            wifiMode = WifiConditionMode.EXCLUDE_LIST,
            wifiSsids = setOf("Home-5G", "Home-Guest"),
            mobileMode = MobileConditionMode.ALWAYS,
        )

        // Matching excluded home Wi-Fi: do not run
        val home5G = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = "Home-5G")
        val home5GQuoted = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = "\"Home-5G\"")
        val homeGuest = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = "home-guest") // case-insensitive

        assertFalse(conditions.shouldRun(home5G))
        assertFalse(conditions.shouldRun(home5GQuoted))
        assertFalse(conditions.shouldRun(homeGuest))

        // Different Wi-Fi: runs
        val hotel = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = "Hotel-Public")
        assertTrue(conditions.shouldRun(hotel))

        // Unknown SSID (e.g. location permission not granted): fail safe and run
        val unknown = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = null)
        assertTrue(conditions.shouldRun(unknown))

        // Mobile data still runs
        val mobile = NetworkStateSnapshot(isConnected = true, isMobile = true)
        assertTrue(conditions.shouldRun(mobile))
    }

    @Test
    fun wifi_only_specific_networks() {
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiEnabled = true,
            wifiMode = WifiConditionMode.INCLUDE_LIST,
            wifiSsids = setOf("Office-Secure"),
            mobileMode = MobileConditionMode.NEVER,
        )

        val office = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = "Office-Secure")
        val home = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = "Home")
        val unknown = NetworkStateSnapshot(isConnected = true, isWifi = true, wifiSsid = null)

        assertTrue(conditions.shouldRun(office))
        assertFalse(conditions.shouldRun(home))
        assertFalse(conditions.shouldRun(unknown))
    }

    @Test
    fun only_on_mobile_data() {
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiEnabled = false,
            mobileMode = MobileConditionMode.ALWAYS,
            ethernetEnabled = false,
        )

        val wifi = NetworkStateSnapshot(isConnected = true, isWifi = true)
        val mobile = NetworkStateSnapshot(isConnected = true, isMobile = true)

        assertFalse(conditions.shouldRun(wifi))
        assertTrue(conditions.shouldRun(mobile))
    }

    @Test
    fun never_on_mobile_data() {
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiEnabled = true,
            mobileMode = MobileConditionMode.NEVER,
        )

        val wifi = NetworkStateSnapshot(isConnected = true, isWifi = true)
        val mobile = NetworkStateSnapshot(isConnected = true, isMobile = true)

        assertTrue(conditions.shouldRun(wifi))
        assertFalse(conditions.shouldRun(mobile))
    }

    @Test
    fun only_on_roaming() {
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiEnabled = false,
            mobileMode = MobileConditionMode.ONLY_ROAMING,
        )

        val domestic = NetworkStateSnapshot(isConnected = true, isMobile = true, isRoaming = false)
        val roaming = NetworkStateSnapshot(isConnected = true, isMobile = true, isRoaming = true)

        assertFalse(conditions.shouldRun(domestic))
        assertTrue(conditions.shouldRun(roaming))
    }

    @Test
    fun never_on_roaming() {
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiEnabled = false,
            mobileMode = MobileConditionMode.NEVER_ROAMING,
        )

        val domestic = NetworkStateSnapshot(isConnected = true, isMobile = true, isRoaming = false)
        val roaming = NetworkStateSnapshot(isConnected = true, isMobile = true, isRoaming = true)

        assertTrue(conditions.shouldRun(domestic))
        assertFalse(conditions.shouldRun(roaming))
    }

    @Test
    fun require_unmetered() {
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiEnabled = true,
            mobileMode = MobileConditionMode.ALWAYS,
            requireUnmetered = true,
        )

        val unmeteredWifi = NetworkStateSnapshot(isConnected = true, isWifi = true, isMetered = false)
        val meteredWifi = NetworkStateSnapshot(isConnected = true, isWifi = true, isMetered = true)
        val meteredMobile = NetworkStateSnapshot(isConnected = true, isMobile = true, isMetered = true)

        assertTrue(conditions.shouldRun(unmeteredWifi))
        assertFalse(conditions.shouldRun(meteredWifi))
        assertFalse(conditions.shouldRun(meteredMobile))
    }

    @Test
    fun serialization_round_trip() {
        val original = TunnelRunConditions(
            enabled = true,
            wifiEnabled = true,
            wifiMode = WifiConditionMode.EXCLUDE_LIST,
            wifiSsids = setOf("MyHome", "OfficeNet"),
            mobileMode = MobileConditionMode.NEVER_ROAMING,
            ethernetEnabled = false,
            requireUnmetered = true,
        )

        val formatted = original.toFormatString()
        val parsed = TunnelRunConditions.parse(formatted)

        assertEquals(original, parsed)
    }

    @Test
    fun handles_empty_or_defaults() {
        val defaultConditions = TunnelRunConditions()
        val formatted = defaultConditions.toFormatString()
        val parsed = TunnelRunConditions.parse(formatted)
        assertEquals(defaultConditions, parsed)
    }
}
