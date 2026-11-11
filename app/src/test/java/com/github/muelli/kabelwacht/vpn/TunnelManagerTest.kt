// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.muelli.kabelwacht.data.ConfigStore
import com.github.muelli.kabelwacht.data.SettingsStore
import com.github.muelli.kabelwacht.data.TunnelProfile
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig
import kotlin.coroutines.CoroutineContext

/**
 * The request handling and the recovery from the system stopping a tunnel right after
 * a switch, against a [FakeBackend] that behaves like GoBackend, including its late
 * VpnService.onDestroy().
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one builds the container, which loads wireguard-go.
@RobolectricConfig(sdk = [34], application = android.app.Application::class)
class TunnelManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val backend = FakeBackend()

    // Stands in for the main thread, where a recovery queues up behind onDestroy().
    private val main = QueuedDispatcher()
    private var clock = 0L

    private val manager = TunnelManager(
        context = context,
        settings = SettingsStore(context),
        backend = backend,
        now = { clock },
        ioDispatcher = Dispatchers.Unconfined,
        mainDispatcher = main,
    )

    private val alerts = mutableListOf<String>()
    private val collector: Job = CoroutineScope(Dispatchers.Unconfined).launch {
        manager.unexpectedlyDown.collect { alerts += it }
    }

    @After
    fun stopCollecting() = collector.cancel()

    private val config: Config = ConfigStore.parse(
        """
        [Interface]
        PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
        Address = 10.0.0.2/32

        [Peer]
        PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
        Endpoint = 192.95.5.69:51820
        AllowedIPs = 0.0.0.0/0
        """.trimIndent(),
    )

    private fun profile(name: String) = TunnelProfile(name, config)

    private val a = profile("a")
    private val b = profile("b")
    private val c = profile("c")

    private fun up(p: TunnelProfile) = runBlocking { manager.setTunnelState(p, up = true) }

    private fun down(p: TunnelProfile) = runBlocking { manager.setTunnelState(p, up = false) }

    private fun running(): Set<String> = backend.runningTunnelNames

    private fun reconnect(p: TunnelProfile) = runBlocking { manager.reconnect(p) }

    @Test
    fun a_requested_up_and_down_raise_nothing() {
        up(a)
        assertEquals(setOf("a"), running())
        assertEquals("a", manager.activeTunnel.value)

        down(a)
        assertTrue(running().isEmpty())
        assertEquals(null, manager.activeTunnel.value)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun a_switch_brings_the_previous_tunnel_down_without_an_alert() {
        up(a)
        up(b)

        assertEquals(setOf("b"), running())
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun a_down_just_after_a_switch_restarts_the_tunnel_exactly_once() {
        up(a)
        up(b)
        clock += 100
        backend.systemStopsService()
        assertTrue(running().isEmpty())

        main.runPending()

        assertEquals(setOf("b"), running())
        assertEquals(2, backend.upsOf("b"))
        assertTrue(alerts.isEmpty())

        // The restart is not itself eligible for another one.
        backend.systemStopsService()
        main.runPending()
        assertTrue(running().isEmpty())
        assertEquals(listOf("b"), alerts)
    }

    @Test
    fun a_newer_request_cancels_the_restart() {
        up(a)
        up(b)
        backend.systemStopsService()

        up(c)
        main.runPending()

        assertEquals(setOf("c"), running())
        assertEquals(1, backend.upsOf("b"))
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun a_down_outside_the_window_raises_an_alert() {
        up(a)
        up(b)
        clock += TunnelManager.SWITCH_RACE_MILLIS + 1
        backend.systemStopsService()
        main.runPending()

        assertTrue(running().isEmpty())
        assertEquals(listOf("b"), alerts)
    }

    @Test
    fun a_down_of_a_tunnel_that_was_never_switched_to_raises_an_alert() {
        up(a)
        backend.systemStopsService()
        main.runPending()

        assertTrue(running().isEmpty())
        assertEquals(listOf("a"), alerts)
    }

    @Test
    fun a_down_then_up_is_covered_like_a_switch() {
        up(a)
        runBlocking { manager.bringDownActive() }
        clock += 100
        up(b)
        // The old service's onDestroy() only now gets to run.
        backend.systemStopsService()
        assertTrue(running().isEmpty())

        main.runPending()

        assertEquals(setOf("b"), running())
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun a_quick_off_and_on_of_the_same_tunnel_is_covered_too() {
        up(a)
        down(a)
        clock += 100
        up(a)
        backend.systemStopsService()

        main.runPending()

        assertEquals(setOf("a"), running())
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun an_up_long_after_a_down_is_not_restarted() {
        up(a)
        runBlocking { manager.bringDownActive() }
        clock += TunnelManager.SWITCH_RACE_MILLIS + 1
        up(b)
        backend.systemStopsService()
        main.runPending()

        assertTrue(running().isEmpty())
        assertEquals(listOf("b"), alerts)
    }

    @Test
    fun a_failed_restart_raises_an_alert() {
        up(a)
        up(b)
        backend.systemStopsService()
        backend.failNextUp = true

        main.runPending()

        assertTrue(running().isEmpty())
        assertEquals(listOf("b"), alerts)
    }

    @Test
    fun an_up_hands_the_backend_a_fresh_copy_of_the_config() {
        up(a)

        // New objects, so the endpoint hostnames are resolved again rather than
        // taken from what the previous Config remembered; the same configuration.
        val given = backend.lastUpConfig!!
        assertNotSame(a.config, given)
        assertEquals(a.config.toWgQuickString(), given.toWgQuickString())
    }

    @Test
    fun a_reconnect_takes_the_tunnel_down_and_up_again_without_an_alert() {
        up(a)

        reconnect(a)

        assertEquals(setOf("a"), running())
        assertEquals(2, backend.upsOf("a"))
        assertEquals("a", manager.activeTunnel.value)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun a_reconnect_of_a_tunnel_that_is_not_up_does_nothing() {
        up(a)
        reconnect(b)
        assertEquals(setOf("a"), running())
        assertEquals(1, backend.upsOf("a"))
        assertEquals(0, backend.upsOf("b"))

        down(a)
        reconnect(a)
        assertTrue(running().isEmpty())
        assertEquals(1, backend.upsOf("a"))
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun a_reconnect_that_cannot_bring_the_tunnel_up_again_fails_and_raises_an_alert() {
        up(a)
        backend.failNextUp = true

        val result = runCatching { reconnect(a) }

        assertTrue(result.isFailure)
        assertTrue(running().isEmpty())
        assertEquals(listOf("a"), alerts)
    }

    @Test
    fun a_stray_down_right_after_a_reconnect_restarts_the_tunnel_once() {
        up(a)
        reconnect(a)
        clock += 100
        backend.systemStopsService()

        main.runPending()

        assertEquals(setOf("a"), running())
        assertEquals(3, backend.upsOf("a"))
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun endpoints_given_as_addresses_are_not_tracked() {
        up(a)
        assertTrue(manager.endpointsInUse("a").isEmpty())
    }

    @Test
    fun addresses_and_hostnames_are_told_apart() {
        assertTrue(TunnelManager.isAddressLiteral("192.95.5.69"))
        assertTrue(TunnelManager.isAddressLiteral("2001:db8::1"))
        assertFalse(TunnelManager.isAddressLiteral("vpn.example.org"))
        assertFalse(TunnelManager.isAddressLiteral("1.2.3.4.example.org"))
    }

    @Test
    fun taking_down_whatever_is_active_raises_nothing() {
        up(a)
        runBlocking { manager.bringDownActive() }
        main.runPending()

        assertTrue(running().isEmpty())
        assertTrue(alerts.isEmpty())
    }
}

/** A [Backend] that, like GoBackend, runs one tunnel and tells it about every state change. */
private class FakeBackend : Backend {
    private var current: Tunnel? = null
    private val ups = mutableMapOf<String, Int>()

    /** Makes the next UP throw, as for a bad config or lost VPN consent. */
    var failNextUp = false

    fun upsOf(name: String): Int = ups[name] ?: 0

    /** The config the latest UP was given. */
    var lastUpConfig: Config? = null

    /** What VpnService.onDestroy() does when it runs late: stops whatever is current. */
    fun systemStopsService() {
        val tunnel = current ?: return
        current = null
        tunnel.onStateChange(Tunnel.State.DOWN)
    }

    override fun getRunningTunnelNames(): Set<String> = setOfNotNull(current?.name)

    override fun getState(tunnel: Tunnel): Tunnel.State =
        if (tunnel === current) Tunnel.State.UP else Tunnel.State.DOWN

    override fun getStatistics(tunnel: Tunnel): Statistics = throw UnsupportedOperationException()

    override fun getVersion(): String = "fake"

    override fun isAlwaysOn(): Boolean = false

    override fun isLockdownEnabled(): Boolean = false

    override fun setState(tunnel: Tunnel, state: Tunnel.State, config: Config?): Tunnel.State {
        if (state == Tunnel.State.UP) {
            if (failNextUp) {
                failNextUp = false
                throw IllegalStateException("cannot bring ${tunnel.name} up")
            }
            current?.takeIf { it !== tunnel }?.let { old ->
                current = null
                old.onStateChange(Tunnel.State.DOWN)
            }
            current = tunnel
            lastUpConfig = config
            ups[tunnel.name] = upsOf(tunnel.name) + 1
            tunnel.onStateChange(Tunnel.State.UP)
        } else if (state == Tunnel.State.DOWN && tunnel === current) {
            current = null
            tunnel.onStateChange(Tunnel.State.DOWN)
        }
        return getState(tunnel)
    }
}

/** Holds dispatched work until [runPending], like a main looper that has not run yet. */
private class QueuedDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    fun runPending() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }
}
