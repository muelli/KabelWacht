// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui.conditions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.muelli.kabelwacht.data.ConditionsStore
import com.github.muelli.kabelwacht.data.MobileConditionMode
import com.github.muelli.kabelwacht.data.NetworkStateSnapshot
import com.github.muelli.kabelwacht.data.TunnelRunConditions
import com.github.muelli.kabelwacht.data.WifiConditionMode
import com.github.muelli.kabelwacht.vpn.automation.NetworkStateMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class RunConditionsViewModel(
    private val conditionsStore: ConditionsStore,
    private val networkMonitor: NetworkStateMonitor,
    lockdownSeen: Flow<Boolean> = flowOf(false),
) : ViewModel() {

    /** Lockdown was on when a tunnel last connected: run conditions then leave no network but the VPN. */
    val lockdownSeen: StateFlow<Boolean> = lockdownSeen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var tunnelName: String = ""
        private set

    var conditions by mutableStateOf(TunnelRunConditions())
        private set

    var ssidInput by mutableStateOf("")

    val networkSnapshot: StateFlow<NetworkStateSnapshot> = networkMonitor.networkSnapshot

    fun start(name: String) {
        if (tunnelName == name) return
        tunnelName = name
        conditions = conditionsStore.get(name)
    }

    /**
     * Other tunnels whose run conditions compete with the ones being edited on
     * some network, in list order. Only one tunnel can be active, so there the
     * running tunnel stays and otherwise the first in the list wins.
     */
    val overlappingTunnels: List<String>
        get() = conditionsStore.conditions.value
            .filter { (name, other) -> name != tunnelName && conditions.overlapsWith(other) }
            .keys
            .sortedBy { it.lowercase() }

    fun onEnabledChange(value: Boolean) {
        conditions = conditions.copy(enabled = value)
    }

    fun onWifiEnabledChange(value: Boolean) {
        conditions = conditions.copy(wifiEnabled = value)
    }

    fun onWifiModeChange(mode: WifiConditionMode) {
        conditions = conditions.copy(wifiMode = mode)
    }

    fun onAddSsid(ssid: String) {
        val trimmed = ssid.trim('"', ' ')
        if (trimmed.isNotEmpty()) {
            conditions = conditions.copy(wifiSsids = conditions.wifiSsids + trimmed)
            ssidInput = ""
        }
    }

    fun onRemoveSsid(ssid: String) {
        conditions = conditions.copy(wifiSsids = conditions.wifiSsids - ssid)
    }

    fun onMobileModeChange(mode: MobileConditionMode) {
        conditions = conditions.copy(mobileMode = mode)
    }

    fun onEthernetEnabledChange(value: Boolean) {
        conditions = conditions.copy(ethernetEnabled = value)
    }

    fun onRequireUnmeteredChange(value: Boolean) {
        conditions = conditions.copy(requireUnmetered = value)
    }

    fun save() {
        if (tunnelName.isNotBlank()) {
            conditionsStore.save(tunnelName, conditions)
        }
    }
}
