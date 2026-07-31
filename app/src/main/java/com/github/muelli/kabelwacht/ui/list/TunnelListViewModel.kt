// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui.list

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.muelli.kabelwacht.data.TunnelProfile
import com.github.muelli.kabelwacht.data.TunnelRepository
import com.github.muelli.kabelwacht.vpn.TunnelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TunnelListViewModel(
    private val repository: TunnelRepository,
    private val tunnelManager: TunnelManager,
) : ViewModel() {

    val profiles: StateFlow<List<TunnelProfile>> = repository.profiles
    val activeTunnel: StateFlow<String?> = tunnelManager.activeTunnel
    val alwaysOnTunnel: kotlinx.coroutines.flow.Flow<String?> = tunnelManager.alwaysOnTunnel

    private val _message = MutableStateFlow<String?>(null)
    /** Transient user-facing message (shown in a snackbar), or null. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** VPN consent intent to launch before activating a tunnel, or null if already granted. */
    fun consentIntent(): Intent? = tunnelManager.consentIntent()

    fun onResume() = repository.refresh()

    /** Bring [profile] up (assumes VPN consent already granted) or down. */
    fun setActive(profile: TunnelProfile, up: Boolean) {
        viewModelScope.launch {
            runCatching { tunnelManager.setTunnelState(profile, up) }
                .onFailure { _message.value = it.message ?: "Could not change tunnel state" }
        }
    }

    fun delete(profile: TunnelProfile) {
        viewModelScope.launch {
            // Tear the tunnel down first if it is the active one.
            if (activeTunnel.value == profile.name) {
                runCatching { tunnelManager.setTunnelState(profile, false) }
            }
            repository.delete(profile.name)
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }
}
