// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

enum class WifiConditionMode {
    ANY,
    EXCLUDE_LIST,
    INCLUDE_LIST,
}

enum class MobileConditionMode {
    ALWAYS,
    NEVER,
    ONLY_ROAMING,
    NEVER_ROAMING,
}

/**
 * User-configurable conditions under which a WireGuard tunnel should automatically run.
 */
data class TunnelRunConditions(
    val enabled: Boolean = false,
    val wifiEnabled: Boolean = true,
    val wifiMode: WifiConditionMode = WifiConditionMode.ANY,
    val wifiSsids: Set<String> = emptySet(),
    val mobileMode: MobileConditionMode = MobileConditionMode.ALWAYS,
    val ethernetEnabled: Boolean = true,
    val requireUnmetered: Boolean = false,
) {
    /**
     * Evaluates whether the tunnel should be connected for the given [network] snapshot.
     */
    fun shouldRun(network: NetworkStateSnapshot): Boolean {
        if (!enabled) return false
        if (!network.isConnected) return false
        if (requireUnmetered && network.isMetered) return false

        if (network.isWifi) {
            if (!wifiEnabled) return false
            val currentSsid = network.wifiSsid?.trim('"', ' ')
            return when (wifiMode) {
                WifiConditionMode.ANY -> true
                WifiConditionMode.EXCLUDE_LIST -> {
                    if (currentSsid.isNullOrBlank()) {
                        // If SSID cannot be determined (e.g. no location permission), default
                        // to running the VPN to keep untrusted/unknown traffic protected.
                        true
                    } else {
                        wifiSsids.none { it.trim().equals(currentSsid, ignoreCase = true) }
                    }
                }
                WifiConditionMode.INCLUDE_LIST -> {
                    if (currentSsid.isNullOrBlank()) {
                        false
                    } else {
                        wifiSsids.any { it.trim().equals(currentSsid, ignoreCase = true) }
                    }
                }
            }
        }

        if (network.isMobile) {
            return when (mobileMode) {
                MobileConditionMode.ALWAYS -> true
                MobileConditionMode.NEVER -> false
                MobileConditionMode.ONLY_ROAMING -> network.isRoaming
                MobileConditionMode.NEVER_ROAMING -> !network.isRoaming
            }
        }

        if (network.isEthernet) {
            return ethernetEnabled
        }

        return false
    }

    fun toFormatString(): String = buildString {
        appendLine("enabled = $enabled")
        appendLine("wifiEnabled = $wifiEnabled")
        appendLine("wifiMode = ${wifiMode.name}")
        appendLine("wifiSsids = ${wifiSsids.joinToString(", ")}")
        appendLine("mobileMode = ${mobileMode.name}")
        appendLine("ethernetEnabled = $ethernetEnabled")
        appendLine("requireUnmetered = $requireUnmetered")
    }

    companion object {
        fun parse(text: String): TunnelRunConditions {
            val map = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx == -1) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }.toMap()

            return TunnelRunConditions(
                enabled = map["enabled"]?.toBooleanStrictOrNull() ?: false,
                wifiEnabled = map["wifiEnabled"]?.toBooleanStrictOrNull() ?: true,
                wifiMode = map["wifiMode"]?.let {
                    runCatching { WifiConditionMode.valueOf(it) }.getOrNull()
                } ?: WifiConditionMode.ANY,
                wifiSsids = map["wifiSsids"]?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toSet() ?: emptySet(),
                mobileMode = map["mobileMode"]?.let {
                    runCatching { MobileConditionMode.valueOf(it) }.getOrNull()
                } ?: MobileConditionMode.ALWAYS,
                ethernetEnabled = map["ethernetEnabled"]?.toBooleanStrictOrNull() ?: true,
                requireUnmetered = map["requireUnmetered"]?.toBooleanStrictOrNull() ?: false,
            )
        }
    }
}

/**
 * Snapshot of the current physical network environment (excluding VPN virtual interfaces).
 */
data class NetworkStateSnapshot(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val wifiSsid: String? = null,
    val isMobile: Boolean = false,
    val isRoaming: Boolean = false,
    val isEthernet: Boolean = false,
    val isMetered: Boolean = false,
)
