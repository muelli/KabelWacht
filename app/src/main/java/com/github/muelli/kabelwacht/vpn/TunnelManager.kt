package com.github.muelli.kabelwacht.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.github.muelli.kabelwacht.data.TunnelProfile
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Wraps the userspace WireGuard [GoBackend] (wireguard-go, no root) and tracks which
 * tunnel is currently up.
 *
 * v1 keeps at most one tunnel active at a time: bringing one up first tears down any
 * other. The name of the active tunnel is exposed as [activeTunnel] for the UI.
 */
class TunnelManager(context: Context) {

    private val appContext = context.applicationContext
    private val backend: Backend = GoBackend(appContext)

    // Reuse one WgTunnel per name so the backend's identity checks stay stable.
    private val tunnels = mutableMapOf<String, WgTunnel>()

    private val _activeTunnel = MutableStateFlow<String?>(null)
    val activeTunnel: StateFlow<String?> = _activeTunnel.asStateFlow()

    init {
        refreshActive()
    }

    /**
     * If the system has not yet granted this app permission to run a VPN, returns the
     * consent [Intent] to launch; otherwise returns null. Must be called (and any
     * returned intent completed) before [setTunnelState] with `up = true`.
     */
    fun consentIntent(): Intent? = VpnService.prepare(appContext)

    /** True when VPN permission is already granted. */
    fun hasConsent(): Boolean = consentIntent() == null

    /**
     * Bring [profile] up or down. Runs the (blocking) backend call off the main
     * thread. Throws on failure (e.g. missing VPN consent, bad config).
     */
    suspend fun setTunnelState(profile: TunnelProfile, up: Boolean) = withContext(Dispatchers.IO) {
        val tunnel = tunnels.getOrPut(profile.name) {
            WgTunnel(profile.name) { refreshActive() }
        }
        if (up) {
            // Enforce a single active tunnel.
            backend.runningTunnelNames
                .filter { it != profile.name }
                .forEach { running -> tunnels[running]?.let { bringDown(it) } }
        }
        backend.setState(tunnel, if (up) Tunnel.State.UP else Tunnel.State.DOWN, profile.config)
        refreshActive()
    }

    private fun bringDown(tunnel: WgTunnel) {
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
    }

    private fun refreshActive() {
        _activeTunnel.value = backend.runningTunnelNames.firstOrNull()
    }
}
