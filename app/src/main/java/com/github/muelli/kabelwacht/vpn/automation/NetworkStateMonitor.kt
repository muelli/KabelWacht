// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.automation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.github.muelli.kabelwacht.data.NetworkStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Monitors physical network transitions (Wi-Fi, Cellular, Ethernet) excluding VPN interfaces.
 */
class NetworkStateMonitor(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _networkSnapshot = MutableStateFlow(NetworkStateSnapshot())
    val networkSnapshot: StateFlow<NetworkStateSnapshot> = _networkSnapshot.asStateFlow()

    private val activeNetworks = mutableMapOf<Network, NetworkCapabilities>()
    private var isRegistered = false

    /**
     * From API 31 the system redacts the SSID out of the callback's
     * `transportInfo` unless the callback opts in with
     * FLAG_INCLUDE_LOCATION_INFO (and fine location is granted); the older
     * WifiManager fallback is background-restricted. Opting in is what keeps
     * SSID rules working while monitoring runs in the background.
     */
    private inner class Callback : ConnectivityManager.NetworkCallback {
        constructor() : super()

        @RequiresApi(Build.VERSION_CODES.S)
        constructor(flags: Int) : super(flags)

        override fun onAvailable(network: Network) {
            val caps = connectivityManager?.getNetworkCapabilities(network)
            if (caps != null && isPhysicalInternetNetwork(caps)) {
                synchronized(activeNetworks) {
                    activeNetworks[network] = caps
                }
                updateSnapshot()
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (isPhysicalInternetNetwork(networkCapabilities)) {
                synchronized(activeNetworks) {
                    activeNetworks[network] = networkCapabilities
                }
                updateSnapshot()
            } else {
                synchronized(activeNetworks) {
                    activeNetworks.remove(network)
                }
                updateSnapshot()
            }
        }

        override fun onLost(network: Network) {
            synchronized(activeNetworks) {
                activeNetworks.remove(network)
            }
            updateSnapshot()
        }
    }

    private val networkCallback: ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Callback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
        } else {
            Callback()
        }

    init {
        start()
    }

    fun start() {
        val cm = connectivityManager ?: return
        if (isRegistered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            // NET_CAPABILITY_NOT_VPN is included by default in NetworkRequest.Builder!
            .build()
        runCatching {
            cm.registerNetworkCallback(request, networkCallback)
            isRegistered = true
        }
        // Seed initial state if available
        val current = cm.activeNetwork
        if (current != null) {
            cm.getNetworkCapabilities(current)?.let { caps ->
                if (isPhysicalInternetNetwork(caps)) {
                    synchronized(activeNetworks) {
                        activeNetworks[current] = caps
                    }
                }
            }
        }
        updateSnapshot()
    }

    fun stop() {
        val cm = connectivityManager ?: return
        if (!isRegistered) return
        runCatching {
            cm.unregisterNetworkCallback(networkCallback)
            isRegistered = false
        }
        synchronized(activeNetworks) {
            activeNetworks.clear()
        }
        _networkSnapshot.value = NetworkStateSnapshot()
    }

    private fun isPhysicalInternetNetwork(caps: NetworkCapabilities): Boolean {
        // Exclude VPN interfaces so the VPN itself doesn't trigger loops
        val isNotVpn = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return isNotVpn && hasInternet
    }

    private fun updateSnapshot() {
        val selectedCaps = synchronized(activeNetworks) {
            // Prioritize Wi-Fi over Cellular if both are connected
            activeNetworks.values.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
                ?: activeNetworks.values.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }
                ?: activeNetworks.values.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }
                ?: activeNetworks.values.firstOrNull()
        }

        val snapshot = if (selectedCaps == null) {
            NetworkStateSnapshot(isConnected = false)
        } else {
            val isWifi = selectedCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isMobile = selectedCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isEthernet = selectedCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val isRoaming = !selectedCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            val isMetered = !selectedCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val ssid = if (isWifi) resolveCurrentSsid(selectedCaps) else null

            NetworkStateSnapshot(
                isConnected = true,
                isWifi = isWifi,
                wifiSsid = ssid,
                isMobile = isMobile,
                isRoaming = isRoaming,
                isEthernet = isEthernet,
                isMetered = isMetered,
            )
        }

        scope.launch {
            _networkSnapshot.value = snapshot
        }
    }

    fun resolveCurrentSsid(caps: NetworkCapabilities? = null): String? {
        val rawSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps != null) {
            (caps.transportInfo as? WifiInfo)?.ssid ?: wifiManager?.connectionInfo?.ssid
        } else {
            wifiManager?.connectionInfo?.ssid
        }
        return normalizeSsid(rawSsid)
    }

    companion object {
        fun normalizeSsid(rawSsid: String?): String? {
            if (rawSsid == null) return null
            val unquoted = rawSsid.removeSurrounding("\"").trim()
            if (unquoted.isEmpty() ||
                unquoted == WifiManager.UNKNOWN_SSID ||
                unquoted.equals("<unknown ssid>", ignoreCase = true)
            ) {
                return null
            }
            return unquoted
        }
    }
}
