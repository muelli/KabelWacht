// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui.conditions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.github.muelli.kabelwacht.data.ConditionsStore
import com.github.muelli.kabelwacht.data.MobileConditionMode
import com.github.muelli.kabelwacht.data.NetworkStateSnapshot
import com.github.muelli.kabelwacht.data.TunnelRunConditions
import com.github.muelli.kabelwacht.data.WifiConditionMode
import com.github.muelli.kabelwacht.vpn.automation.NetworkStateMonitor
import kotlinx.coroutines.flow.StateFlow

class RunConditionsViewModel(
    private val conditionsStore: ConditionsStore,
    private val networkMonitor: NetworkStateMonitor,
) : ViewModel() {

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
