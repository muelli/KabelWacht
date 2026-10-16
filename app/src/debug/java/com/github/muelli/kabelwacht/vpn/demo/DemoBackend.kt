// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.demo

import android.content.Context
import android.net.VpnService
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.DemoPeerStats
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.demoStatistics
import com.wireguard.config.Config
import com.wireguard.crypto.Key
import java.net.Inet4Address

/**
 * A [Backend] that pretends to connect, for screenshots and videos: tunnels
 * come up instantly and report [DemoTraffic], and [DemoVpnService] opens a real
 * but empty VPN interface (an address, no routes) so Android shows its key icon
 * and "VPN connected" without any traffic going through it.
 *
 * Like the real backend, at most one tunnel runs at a time.
 */
class DemoBackend(
    private val vpn: DemoVpn,
    private val clock: () -> Long = System::currentTimeMillis,
) : Backend {

    private class Session(val tunnel: Tunnel, val peers: List<Key>, val startEpochMillis: Long)

    private val sessions = mutableMapOf<String, Session>()

    @Synchronized
    override fun getRunningTunnelNames(): Set<String> = sessions.keys.toSet()

    @Synchronized
    override fun getState(tunnel: Tunnel): Tunnel.State =
        if (tunnel.name in sessions) Tunnel.State.UP else Tunnel.State.DOWN

    @Synchronized
    override fun getStatistics(tunnel: Tunnel): Statistics {
        val session = sessions[tunnel.name] ?: return demoStatistics(emptyList())
        val elapsed = clock() - session.startEpochMillis
        return demoStatistics(
            session.peers.mapIndexed { i, key ->
                val s = DemoTraffic.sample(session.startEpochMillis, elapsed, i)
                DemoPeerStats(key, s.rxBytes, s.txBytes, s.latestHandshakeEpochMillis)
            },
        )
    }

    override fun getVersion(): String = "demo"

    override fun isAlwaysOn(): Boolean = false

    override fun isLockdownEnabled(): Boolean = false

    @Synchronized
    override fun setState(tunnel: Tunnel, state: Tunnel.State, config: Config?): Tunnel.State {
        val current = getState(tunnel)
        val wanted = if (state == Tunnel.State.TOGGLE) {
            if (current == Tunnel.State.UP) Tunnel.State.DOWN else Tunnel.State.UP
        } else {
            state
        }
        if (wanted == current) return current

        if (wanted == Tunnel.State.UP) {
            requireNotNull(config) { "Cannot bring up ${tunnel.name} without a config" }
            if (!vpn.hasConsent()) throw BackendException(BackendException.Reason.VPN_NOT_AUTHORIZED)
            // Switching tunnels reuses the interface rather than closing it first.
            sessions.values.toList().forEach { stop(it, closeVpn = false) }
            val address = config.`interface`.addresses.firstOrNull { it.address is Inet4Address }
            vpn.open(tunnel.name, address?.address?.hostAddress ?: "10.0.0.2", address?.mask ?: 32)
            sessions[tunnel.name] = Session(tunnel, config.peers.map { it.publicKey }, clock())
            tunnel.onStateChange(Tunnel.State.UP)
        } else {
            sessions[tunnel.name]?.let { stop(it) }
        }
        return wanted
    }

    private fun stop(session: Session, closeVpn: Boolean = true) {
        sessions.remove(session.tunnel.name)
        if (closeVpn && sessions.isEmpty()) vpn.close()
        session.tunnel.onStateChange(Tunnel.State.DOWN)
    }
}

/** The empty VPN interface behind [DemoBackend]; replaceable in tests. */
interface DemoVpn {
    fun hasConsent(): Boolean
    fun open(session: String, address: String, prefixLength: Int)
    fun close()

    class System(private val context: Context) : DemoVpn {
        override fun hasConsent(): Boolean = VpnService.prepare(context) == null
        override fun open(session: String, address: String, prefixLength: Int) =
            DemoVpnService.open(context, session, address, prefixLength)
        override fun close() = DemoVpnService.close(context)
    }
}
