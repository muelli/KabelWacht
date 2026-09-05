// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.automation

import com.github.muelli.kabelwacht.data.ConditionsStore
import com.github.muelli.kabelwacht.data.NetworkStateSnapshot
import com.github.muelli.kabelwacht.data.TunnelRepository
import com.github.muelli.kabelwacht.vpn.TunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Coordinates automated tunnel activation and deactivation based on physical network transitions
 * and user-configured [TunnelRunConditions].
 */
class AutomationEngine(
    private val conditionsStore: ConditionsStore,
    private val repository: TunnelRepository,
    private val tunnelManager: TunnelManager,
    val networkMonitor: NetworkStateMonitor,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** True if any configured tunnel currently has run conditions enabled. */
    val hasActiveAutomations: StateFlow<Boolean> = conditionsStore.conditions
        .map { map -> map.values.any { it.enabled } }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private var lastEvaluatedNetwork: NetworkStateSnapshot? = null
    private var manuallySuppressedTunnel: String? = null
    private var evaluationJob: Job? = null

    init {
        scope.launch {
            combine(
                networkMonitor.networkSnapshot,
                conditionsStore.conditions,
            ) { snapshot, conditions ->
                Pair(snapshot, conditions)
            }.collect { (snapshot, conditions) ->
                onNetworkOrConditionsChanged(snapshot, conditions)
            }
        }
    }

    /**
     * Notify the engine of an explicit user manual toggle on/off.
     * Prevents automation from immediately fighting the user when they manually disconnect.
     */
    fun onUserManualToggle(tunnelName: String, up: Boolean) {
        if (!up) {
            manuallySuppressedTunnel = tunnelName
        } else {
            manuallySuppressedTunnel = null
        }
    }

    private fun onNetworkOrConditionsChanged(
        snapshot: NetworkStateSnapshot,
        conditions: Map<String, com.github.muelli.kabelwacht.data.TunnelRunConditions>,
    ) {
        // Debounce slightly to allow network state to settle
        evaluationJob?.cancel()
        evaluationJob = scope.launch {
            delay(300)
            evaluate(snapshot, conditions)
        }
    }

    private suspend fun evaluate(
        snapshot: NetworkStateSnapshot,
        conditions: Map<String, com.github.muelli.kabelwacht.data.TunnelRunConditions>,
    ) {
        // If physical network changed significantly, clear manual suppression
        if (lastEvaluatedNetwork != null && hasNetworkEnvironmentChanged(lastEvaluatedNetwork!!, snapshot)) {
            manuallySuppressedTunnel = null
        }
        lastEvaluatedNetwork = snapshot

        val activeName = tunnelManager.activeTunnel.value

        // Find the first tunnel whose conditions match the current network
        val matchingProfile = conditions.entries
            .filter { it.value.enabled }
            .firstOrNull { (_, cond) -> cond.shouldRun(snapshot) }
            ?.let { (name, _) -> repository.get(name) }

        if (matchingProfile != null) {
            // A condition matches
            if (activeName == matchingProfile.name) {
                // Already active, nothing to do
                return
            }
            if (manuallySuppressedTunnel == matchingProfile.name) {
                // User manually turned this off on this network; respect their choice
                return
            }
            if (!tunnelManager.hasConsent()) {
                // Missing VPN permission, cannot automatically start
                return
            }
            runCatching {
                tunnelManager.setTunnelState(matchingProfile, up = true)
            }
        } else {
            // No condition matches.
            // If an active tunnel has conditions enabled (auto-managed), bring it down!
            if (activeName != null) {
                val activeConditions = conditions[activeName]
                if (activeConditions?.enabled == true) {
                    runCatching {
                        tunnelManager.bringDownActive()
                    }
                }
            }
        }
    }

    private fun hasNetworkEnvironmentChanged(old: NetworkStateSnapshot, new: NetworkStateSnapshot): Boolean {
        if (old.isConnected != new.isConnected) return true
        if (old.isWifi != new.isWifi) return true
        if (old.isMobile != new.isMobile) return true
        if (old.isWifi && new.isWifi && old.wifiSsid != new.wifiSsid) return true
        if (old.isMobile && new.isMobile && old.isRoaming != new.isRoaming) return true
        return false
    }
}
