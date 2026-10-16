// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.automation

import com.github.muelli.kabelwacht.data.ConditionsStore
import com.github.muelli.kabelwacht.data.NetworkStateSnapshot
import com.github.muelli.kabelwacht.data.TunnelRepository
import com.github.muelli.kabelwacht.vpn.TunnelManager
import kotlinx.coroutines.CancellationException
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
 *
 * [onAutoConnectFailed] reports a tunnel the rules wanted up but that could not be
 * connected, with the cause — or null when VPN consent is missing — since nobody
 * is watching the screen to notice.
 */
class AutomationEngine(
    private val conditionsStore: ConditionsStore,
    private val repository: TunnelRepository,
    private val tunnelManager: TunnelManager,
    val networkMonitor: NetworkStateMonitor,
    private val onAutoConnectFailed: (tunnelName: String, cause: Throwable?) -> Unit = { _, _ -> },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** True if any configured tunnel currently has run conditions enabled. */
    val hasActiveAutomations: StateFlow<Boolean> = conditionsStore.conditions
        .map { map -> map.values.any { it.enabled } }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private var lastEvaluatedNetwork: NetworkStateSnapshot? = null

    // Written from UI threads, read by the evaluation coroutine.
    @Volatile
    private var manualOverride: ManualOverride? = null
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
     * Notify the engine of an explicit user manual toggle on/off, so automation
     * does not fight the user: their choice holds until the network changes.
     */
    fun onUserManualToggle(tunnelName: String, up: Boolean) {
        manualOverride = ManualOverride(tunnelName, up)
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
        // A real change of network ends the user's manual override.
        val previous = lastEvaluatedNetwork
        if (previous != null && hasNetworkEnvironmentChanged(previous, snapshot)) {
            manualOverride = null
        }
        lastEvaluatedNetwork = snapshot

        val action = decideAutomation(
            snapshot = snapshot,
            conditions = conditions,
            tunnelOrder = repository.profiles.value.map { it.name },
            activeName = tunnelManager.activeTunnel.value,
            override = manualOverride,
        )
        when (action) {
            AutomationAction.None -> Unit
            is AutomationAction.Start -> {
                val profile = repository.get(action.name) ?: return
                // Without VPN permission nothing can be started automatically.
                if (!tunnelManager.hasConsent()) {
                    onAutoConnectFailed(profile.name, null)
                    return
                }
                runCatching { tunnelManager.setTunnelState(profile, up = true) }
                    .onFailure {
                        // A newer network change superseding this evaluation is no failure.
                        if (it !is CancellationException) onAutoConnectFailed(profile.name, it)
                    }
            }
            AutomationAction.StopActive -> runCatching { tunnelManager.bringDownActive() }
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
