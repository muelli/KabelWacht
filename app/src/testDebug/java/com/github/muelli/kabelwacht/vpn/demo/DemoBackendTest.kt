// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.demo

import com.github.muelli.kabelwacht.data.ConfigStore
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.Tunnel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBackendTest {

    private class FakeVpn(var consent: Boolean = true) : DemoVpn {
        var opened: Triple<String, String, Int>? = null
        var open = false
        var closes = 0
        override fun hasConsent() = consent
        override fun open(session: String, address: String, prefixLength: Int) {
            opened = Triple(session, address, prefixLength)
            open = true
        }
        override fun close() {
            open = false
            closes++
        }
    }

    private class FakeTunnel(private val name: String) : Tunnel {
        val states = mutableListOf<Tunnel.State>()
        override fun getName() = name
        override fun onStateChange(newState: Tunnel.State) {
            states += newState
        }
    }

    private val config = ConfigStore.parse(
        """
        [Interface]
        PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
        Address = 10.8.0.2/24

        [Peer]
        PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
        Endpoint = 192.95.5.69:51820
        AllowedIPs = 0.0.0.0/0
        """.trimIndent(),
    )

    private var now = 1_000_000L
    private val vpn = FakeVpn()
    private val backend = DemoBackend(vpn) { now }

    @Test
    fun up_opens_the_vpn_interface_with_the_tunnel_address() {
        val tunnel = FakeTunnel("home")
        assertEquals(Tunnel.State.UP, backend.setState(tunnel, Tunnel.State.UP, config))
        assertEquals(Triple("home", "10.8.0.2", 24), vpn.opened)
        assertTrue(vpn.open)
        assertEquals(setOf("home"), backend.runningTunnelNames)
        assertEquals(listOf(Tunnel.State.UP), tunnel.states)
    }

    @Test
    fun only_one_tunnel_runs_at_a_time() {
        val a = FakeTunnel("a")
        val b = FakeTunnel("b")
        backend.setState(a, Tunnel.State.UP, config)
        backend.setState(b, Tunnel.State.UP, config)
        assertEquals(setOf("b"), backend.runningTunnelNames)
        assertEquals(listOf(Tunnel.State.UP, Tunnel.State.DOWN), a.states)
        assertTrue(vpn.open)
        // Switching hands the interface over instead of closing it in between.
        assertEquals(0, vpn.closes)
    }

    @Test
    fun down_closes_the_interface() {
        val tunnel = FakeTunnel("home")
        backend.setState(tunnel, Tunnel.State.UP, config)
        assertEquals(Tunnel.State.DOWN, backend.setState(tunnel, Tunnel.State.TOGGLE, null))
        assertFalse(vpn.open)
        assertEquals(Tunnel.State.DOWN, backend.getState(tunnel))
    }

    @Test(expected = BackendException::class)
    fun up_without_vpn_consent_fails_like_the_real_backend() {
        vpn.consent = false
        backend.setState(FakeTunnel("home"), Tunnel.State.UP, config)
    }

    @Test
    fun statistics_report_handshake_and_growing_traffic() {
        val tunnel = FakeTunnel("home")
        backend.setState(tunnel, Tunnel.State.UP, config)
        val early = backend.getStatistics(tunnel)
        assertEquals(0L, early.totalRx())

        now += 30_000
        val later = backend.getStatistics(tunnel)
        assertTrue(later.totalRx() > 0)
        assertTrue(later.totalTx() in 1 until later.totalRx())
        val peer = later.peer(later.peers().single())!!
        assertEquals(1_000_000L + DemoTraffic.FIRST_HANDSHAKE_MS, peer.latestHandshakeEpochMillis())
    }
}
