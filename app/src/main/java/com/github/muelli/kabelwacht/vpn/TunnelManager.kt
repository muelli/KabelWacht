// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import com.github.muelli.kabelwacht.data.ConfigStore
import com.github.muelli.kabelwacht.data.SettingsStore
import com.github.muelli.kabelwacht.data.TunnelProfile
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps the userspace WireGuard [GoBackend] (wireguard-go, no root) and tracks which
 * tunnel is currently up.
 *
 * v1 keeps at most one tunnel active at a time: bringing one up first tears down any
 * other. The name of the active tunnel is exposed as [activeTunnel] for the UI.
 * [backend], [now] and the dispatchers are injectable for tests.
 */
class TunnelManager(
    context: Context,
    private val settings: SettingsStore,
    backend: Backend = GoBackend(context.applicationContext),
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Where a recovery is queued: the main thread, behind VpnService.onDestroy().
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : EndpointTunnels {

    private val appContext = context.applicationContext
    private val backend: Backend = backend

    // Reuse one WgTunnel per name so the backend's identity checks stay stable.
    private val tunnels = mutableMapOf<String, WgTunnel>()

    private val _activeTunnel = MutableStateFlow<String?>(null)
    override val activeTunnel: StateFlow<String?> = _activeTunnel.asStateFlow()

    /** Name of the tunnel Android's always-on VPN feature will (re)connect, if set. */
    val alwaysOnTunnel: Flow<String?> = settings.alwaysOnTunnel

    // The tunnel this app last asked to keep up; only its DOWN is unexpected.
    @Volatile private var wanted: String? = null

    private val requests = Mutex()

    // Incremented on each request, so a recovery can tell whether one came in since.
    private var generation = 0L

    private val _unexpectedlyDown = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Emits a tunnel's name when it went down without this app asking for it. */
    override val unexpectedlyDown: SharedFlow<String> = _unexpectedlyDown.asSharedFlow()

    @Volatile private var lastSwitch: Switch? = null

    // When this app last took a tunnel down; an UP soon after is as exposed to the
    // old VpnService.onDestroy() as a direct switch is.
    @Volatile private var lastDownAt: Long? = null

    // The addresses the running tunnel's hostname endpoints were brought up with.
    @Volatile private var addressesInUse: AddressesInUse? = null

    private val scope = CoroutineScope(SupervisorJob())

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
    suspend fun setTunnelState(profile: TunnelProfile, up: Boolean) = withContext(ioDispatcher) {
        requests.withLock {
            generation++
            lastSwitch = null
            applyState(profile, up)
        }
    }

    /**
     * Bring down whatever tunnel is currently active, if any.
     */
    suspend fun bringDownActive() = withContext(ioDispatcher) {
        requests.withLock {
            generation++
            lastSwitch = null
            changing {
                val running = backend.runningTunnelNames
                running.forEach { bringDown(tunnelFor(it)) }
                if (running.isNotEmpty()) lastDownAt = now()
            }
        }
        refreshActive()
    }

    /**
     * Takes [profile] down and up again, as a request of the app's own (nothing is
     * reported as unexpectedly down on the way). Does nothing if it is not the
     * tunnel that is up. If it cannot be brought up again, that is reported as
     * [unexpectedlyDown] and thrown.
     */
    override suspend fun reconnect(profile: TunnelProfile) = withContext(ioDispatcher) {
        // Not cancellable: stopping between the DOWN and the UP would leave it off.
        withContext(NonCancellable) {
            requests.withLock {
                if (profile.name !in backend.runningTunnelNames) return@withLock
                generation++
                lastSwitch = null
                applyState(profile, up = false)
                try {
                    applyState(profile, up = true)
                } catch (e: Exception) {
                    _unexpectedlyDown.tryEmit(profile.name)
                    throw e
                }
            }
        }
    }

    override fun endpointsInUse(name: String): Map<String, String> =
        addressesInUse?.takeIf { it.tunnel == name && name in backend.runningTunnelNames }
            ?.addresses.orEmpty()

    // Callers hold [requests]. A [recoverable] UP is remembered, so that a stray DOWN
    // right after it can be put right; a recovery itself is not, to retry only once.
    private suspend fun applyState(profile: TunnelProfile, up: Boolean, recoverable: Boolean = true) {
        val tunnel = tunnelFor(profile.name)
        changing {
            if (up) {
                // Enforce a single active tunnel.
                val others = backend.runningTunnelNames.filter { it != profile.name }
                others.forEach { running -> bringDown(tunnelFor(running)) }
                val downRecently = others.isNotEmpty() ||
                    lastDownAt?.let { now() - it < SWITCH_RACE_MILLIS } == true
                if (recoverable && downRecently) {
                    lastSwitch = Switch(profile, now(), generation)
                }
            }
            if (up) {
                // A new Config, so the endpoint hostnames are looked up now: the backend
                // keeps what it resolved on the Config for a couple of minutes.
                val config = freshConfig(profile.config)
                addressesInUse = null
                backend.setState(tunnel, Tunnel.State.UP, config)
                addressesInUse = AddressesInUse(profile.name, hostnameEndpoints(config))
            } else {
                backend.setState(tunnel, Tunnel.State.DOWN, profile.config)
                addressesInUse = null
                lastDownAt = now()
            }
        }
        // Remember the most recently activated tunnel as the always-on target.
        if (up) settings.setAlwaysOnTunnel(profile.name)
        refreshActive()
    }

    /** Current traffic of [name], or null if it is not the active tunnel. */
    override suspend fun statistics(name: String): TunnelStatistics? = withContext(ioDispatcher) {
        if (name !in backend.runningTunnelNames) return@withContext null
        val stats = runCatching { backend.getStatistics(tunnelFor(name)) }.getOrNull()
            ?: return@withContext null
        TunnelStatistics.from(name, stats)
    }

    /**
     * Samples [statistics] of the active tunnel every [intervalMillis] while
     * collected; emits null whenever no tunnel is up.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun statisticsUpdates(intervalMillis: Long = 1000): Flow<TunnelStatistics?> =
        activeTunnel.transformLatest { name ->
            if (name == null) {
                emit(null)
                return@transformLatest
            }
            while (true) {
                emit(statistics(name))
                delay(intervalMillis)
            }
        }

    // The same configuration, parsed anew; the original if that somehow fails.
    private fun freshConfig(config: Config): Config =
        runCatching { ConfigStore.parse(config.toWgQuickString()) }.getOrDefault(config)

    // Hostname endpoints of [config] and the address each was brought up with. The
    // backend resolved them a moment ago and the endpoint remembers the answer, so
    // this reads the address wireguard-go was given rather than looking it up again.
    private fun hostnameEndpoints(config: Config): Map<String, String> {
        val found = mutableMapOf<String, String>()
        for (peer in config.peers) {
            val endpoint = peer.endpoint.orElse(null) ?: continue
            if (isAddressLiteral(endpoint.host)) continue
            val address = endpoint.resolved.orElse(null)?.host ?: continue
            found[endpoint.host] = address
        }
        return found
    }

    private fun tunnelFor(name: String): WgTunnel = synchronized(tunnels) {
        tunnels.getOrPut(name) { WgTunnel(name) { state -> onBackendStateChange(name, state) } }
    }

    private fun onBackendStateChange(name: String, state: Tunnel.State) {
        if (state == Tunnel.State.DOWN) {
            val switch = lastSwitch
            if (switch != null && switch.profile.name == name &&
                now() - switch.at < SWITCH_RACE_MILLIS
            ) {
                // The previous tunnel's VpnService.onDestroy() took the new one down
                // too: bring it back once, after onDestroy() is done.
                lastSwitch = null
                scope.launch(mainDispatcher) { recover(switch) }
            } else if (name == wanted) {
                _unexpectedlyDown.tryEmit(name)
            }
        }
        refreshActive()
    }

    // Brings the tunnel lost to [switch] back up, unless a request came in since.
    private suspend fun recover(switch: Switch) {
        withContext(ioDispatcher) {
            requests.withLock {
                if (generation != switch.generation) return@withLock
                runCatching { applyState(switch.profile, up = true, recoverable = false) }
                    .onFailure { _unexpectedlyDown.tryEmit(switch.profile.name) }
            }
        }
    }

    // Runs backend changes the app asked for, then updates [wanted].
    private inline fun <T> changing(block: () -> T): T {
        wanted = null
        try {
            return block()
        } finally {
            wanted = backend.runningTunnelNames.firstOrNull()
        }
    }

    private fun bringDown(tunnel: WgTunnel) {
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
    }

    private fun refreshActive() {
        _activeTunnel.value = backend.runningTunnelNames.firstOrNull()
    }

    private class AddressesInUse(val tunnel: String, val addresses: Map<String, String>)

    /** [profile] was brought up at [at], by request [generation], right after a tunnel went down. */
    private class Switch(val profile: TunnelProfile, val at: Long, val generation: Long)

    internal companion object {
        /** How long after a tunnel was taken down a stray DOWN of the next one is put down to [Switch]. */
        const val SWITCH_RACE_MILLIS = 5_000L

        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

        /** True for an IPv4 or IPv6 address written out, as opposed to a hostname. */
        internal fun isAddressLiteral(host: String): Boolean = ':' in host || IPV4.matches(host)
    }
}
