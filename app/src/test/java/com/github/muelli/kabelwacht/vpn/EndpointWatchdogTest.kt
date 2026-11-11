// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import com.github.muelli.kabelwacht.data.ConfigStore
import com.github.muelli.kabelwacht.data.TunnelProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class StallTrackerTest {

    // A sample taken at [at] (epoch ms) of a tunnel whose last handshake was at [handshakeAt].
    private fun stats(rx: Long, tx: Long, handshakeAt: Long?, at: Long = 0) = TunnelStatistics(
        tunnelName = "t",
        rxBytes = rx,
        txBytes = tx,
        lastHandshakeEpochMillis = handshakeAt,
        sampledAtMillis = at,
    )

    private val grace = 45_000L

    // The handshakes below happened at this epoch time, give or take.
    private val h = 1_000_000L

    private val tracker = StallTracker(
        stallGraceMillis = grace,
        staleHandshakeSeconds = 200,
        backoffMillis = listOf(30_000, 60_000),
    )

    @Test
    fun an_idle_tunnel_that_never_sent_anything_is_left_alone() {
        assertFalse(tracker.onSample(stats(0, 0, null), now = 0))
        assertFalse(tracker.onSample(stats(0, 0, null), now = grace * 10))
    }

    @Test
    fun a_tunnel_that_sends_without_ever_getting_a_handshake_is_stuck_after_the_grace_period() {
        assertFalse(tracker.onSample(stats(0, 148, null), now = 1_000))
        assertFalse(tracker.onSample(stats(0, 296, null), now = 1_000 + grace - 1))
        assertTrue(tracker.onSample(stats(0, 444, null), now = 1_000 + grace))
    }

    @Test
    fun a_recent_handshake_is_healthy_however_much_is_sent() {
        assertFalse(tracker.onSample(stats(500, 1_000, h, at = h + 10_000), now = 10_000))
        assertFalse(tracker.onSample(stats(500, 9_000, h, at = h + 100_000), now = 100_000))
        assertFalse(tracker.onSample(stats(500, 90_000, h, at = h + 190_000), now = 1_000_000))
    }

    @Test
    fun an_old_handshake_on_an_idle_tunnel_is_not_a_problem() {
        tracker.onSample(stats(500, 1_000, h, at = h + 5_000), now = 5_000)
        // Ten minutes on, nothing has been sent since: it just has not needed a handshake.
        assertFalse(tracker.onSample(stats(500, 1_000, h, at = h + 600_000), now = 600_000))
        assertFalse(tracker.onSample(stats(500, 1_000, h, at = h + 900_000), now = 900_000))
    }

    @Test
    fun an_old_handshake_with_more_sent_since_is_stuck_after_the_grace_period() {
        tracker.onSample(stats(500, 1_000, h, at = h + 5_000), now = 5_000)
        assertFalse(tracker.onSample(stats(500, 1_400, h, at = h + 300_000), now = 300_000))
        assertFalse(tracker.onSample(stats(500, 1_800, h, at = h + 344_999), now = 344_999))
        assertTrue(tracker.onSample(stats(500, 2_000, h, at = h + 345_000), now = 345_000))
    }

    @Test
    fun the_traffic_that_wakes_an_idle_tunnel_does_not_count_when_the_handshake_follows() {
        tracker.onSample(stats(500, 1_000, h, at = h + 5_000), now = 5_000)
        // Traffic after ten idle minutes: stuck for the moment...
        assertFalse(tracker.onSample(stats(500, 1_200, h, at = h + 600_000), now = 600_000))
        // ...until the new handshake a few seconds later.
        val h2 = h + 608_000
        assertFalse(tracker.onSample(stats(700, 1_400, h2, at = h + 610_000), now = 610_000))
        assertFalse(tracker.onSample(stats(900, 1_800, h2, at = h + 658_000), now = 658_000))
    }

    @Test
    fun a_check_without_a_change_waits_longer_each_time() {
        assertFalse(tracker.onSample(stats(0, 100, null), now = 0))
        assertTrue(tracker.onSample(stats(0, 100, null), now = grace))

        tracker.checkedWithoutChange(now = grace)
        assertFalse(tracker.onSample(stats(0, 100, null), now = grace + 29_999))
        assertTrue(tracker.onSample(stats(0, 100, null), now = grace + 30_000))

        tracker.checkedWithoutChange(now = grace + 30_000)
        assertFalse(tracker.onSample(stats(0, 100, null), now = grace + 90_000 - 1))
        assertTrue(tracker.onSample(stats(0, 100, null), now = grace + 90_000))

        // The last interval stays the longest.
        val third = grace + 90_000
        tracker.checkedWithoutChange(now = third)
        assertFalse(tracker.onSample(stats(0, 100, null), now = third + 59_999))
        assertTrue(tracker.onSample(stats(0, 100, null), now = third + 60_000))
    }

    @Test
    fun a_handshake_ends_the_stall_and_starts_the_backoff_over() {
        assertFalse(tracker.onSample(stats(0, 200, null), now = 0))
        assertTrue(tracker.onSample(stats(0, 300, null), now = grace))
        tracker.checkedWithoutChange(now = grace)

        // The peer answered after all.
        assertFalse(tracker.onSample(stats(100, 400, h, at = h + 1_000), now = grace + 5_000))

        // Stuck again later: the first interval applies again, not the second.
        assertFalse(tracker.onSample(stats(100, 500, h, at = h + 400_000), now = 500_000))
        assertTrue(tracker.onSample(stats(100, 600, h, at = h + 450_000), now = 550_000))
        tracker.checkedWithoutChange(now = 550_000)
        assertFalse(tracker.onSample(stats(100, 700, h, at = h + 479_999), now = 579_999))
        assertTrue(tracker.onSample(stats(100, 700, h, at = h + 480_000), now = 580_000))
    }

    @Test
    fun counters_that_start_over_mean_a_new_tunnel() {
        val slow = StallTracker(stallGraceMillis = grace, staleHandshakeSeconds = 200, backoffMillis = listOf(600_000))
        assertFalse(slow.onSample(stats(0, 500, null), now = 0))
        assertTrue(slow.onSample(stats(0, 600, null), now = grace))
        slow.checkedWithoutChange(now = grace)

        // Restarted: the counters are back at zero, so the long wait no longer applies.
        assertFalse(slow.onSample(stats(0, 0, null), now = grace + 1))
        assertFalse(slow.onSample(stats(0, 148, null), now = grace + 2))
        assertTrue(slow.onSample(stats(0, 296, null), now = 2 * grace + 2))
    }
}

class EndpointWatchdogTest {

    private val config = ConfigStore.parse(
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
    private val profile = TunnelProfile("a", config)

    private val scope = CoroutineScope(Dispatchers.Default)

    @After
    fun stop() = scope.cancel()

    private val tunnels = FakeTunnels()
    private val lookups = AtomicInteger()
    private var answer: () -> List<String> = { listOf("203.0.113.7") }

    // Looks stuck at once, and has a long wait between checks.
    private fun watchdog() = EndpointWatchdog(
        tunnels = tunnels,
        profiles = { name -> profile.takeIf { it.name == name } },
        resolver = object : HostResolver {
            override suspend fun resolve(host: String): List<String> {
                lookups.incrementAndGet()
                return answer()
            }
        },
        now = System::currentTimeMillis,
        pollMillis = 5,
        resolveTimeoutMillis = 1_000,
        newTracker = { StallTracker(stallGraceMillis = 0, staleHandshakeSeconds = 200, backoffMillis = listOf(60_000)) },
    )

    private fun start() {
        watchdog().observe(scope)
        tunnels.activeTunnel.value = "a"
    }

    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(5) }
    }

    @Test
    fun an_endpoint_that_moved_gets_the_tunnel_reconnected() {
        tunnels.addresses = mapOf("vpn.example.org" to "198.51.100.1")
        answer = { listOf("203.0.113.7") }

        // Like the real thing, a reconnect brings the tunnel up with the address now answered.
        tunnels.addressAfterReconnect = "203.0.113.7"

        start()
        waitFor("a reconnect") { tunnels.reconnects.get() >= 1 }
        // Not again: the tunnel now uses the address the lookup returns.
        Thread.sleep(100)

        assertEquals(1, tunnels.reconnects.get())
        assertEquals(listOf("a"), tunnels.reconnected)
    }

    @Test
    fun an_endpoint_that_is_where_it_was_is_not_reconnected_and_not_asked_again_at_once() {
        tunnels.addresses = mapOf("vpn.example.org" to "198.51.100.1")
        answer = { listOf("198.51.100.1", "2001:db8::1") }

        start()
        waitFor("a lookup") { lookups.get() >= 1 }
        Thread.sleep(150)

        assertEquals(1, lookups.get())
        assertEquals(0, tunnels.reconnects.get())
    }

    @Test
    fun a_lookup_that_fails_is_no_reason_to_reconnect() {
        tunnels.addresses = mapOf("vpn.example.org" to "198.51.100.1")
        answer = { throw java.io.IOException("no network") }

        start()
        waitFor("a lookup") { lookups.get() >= 1 }
        Thread.sleep(100)

        assertEquals(0, tunnels.reconnects.get())
    }

    @Test
    fun a_lookup_that_answers_nothing_is_no_reason_to_reconnect() {
        tunnels.addresses = mapOf("vpn.example.org" to "198.51.100.1")
        answer = { emptyList() }

        start()
        waitFor("a lookup") { lookups.get() >= 1 }
        Thread.sleep(100)

        assertEquals(0, tunnels.reconnects.get())
    }

    @Test
    fun a_tunnel_with_only_literal_endpoints_is_never_looked_at() {
        tunnels.addresses = emptyMap()

        start()
        Thread.sleep(150)

        assertEquals(0, lookups.get())
        assertEquals(0, tunnels.sampleCount.get())
        assertEquals(0, tunnels.reconnects.get())
    }

    @Test
    fun a_healthy_tunnel_is_not_looked_up() {
        tunnels.addresses = mapOf("vpn.example.org" to "198.51.100.1")
        tunnels.stats = { TunnelStatistics("a", 900, 800, System.currentTimeMillis() - 5_000, System.currentTimeMillis()) }

        start()
        Thread.sleep(150)

        assertEquals(0, lookups.get())
        assertEquals(0, tunnels.reconnects.get())
    }

    @Test
    fun a_failed_reconnect_does_not_stop_the_watching() {
        tunnels.addresses = mapOf("vpn.example.org" to "198.51.100.1")
        tunnels.failReconnect = true

        start()

        // Tried again after the failure, so the watching survived it.
        waitFor("a second reconnect attempt") { tunnels.reconnects.get() >= 2 }
    }
}

private class FakeTunnels : EndpointTunnels {
    override val activeTunnel = MutableStateFlow<String?>(null)
    override val unexpectedlyDown: SharedFlow<String> = MutableSharedFlow()

    @Volatile var addresses: Map<String, String> = emptyMap()
    @Volatile var failReconnect = false

    // The address every hostname endpoint is in use with after a successful reconnect.
    @Volatile var addressAfterReconnect: String? = null

    // Sent without ever being answered.
    @Volatile var stats: () -> TunnelStatistics = { TunnelStatistics("a", 0, 296, null, 0) }

    val sampleCount = AtomicInteger()
    val reconnects = AtomicInteger()
    val reconnected = java.util.Collections.synchronizedList(mutableListOf<String>())

    override suspend fun statistics(name: String): TunnelStatistics? {
        sampleCount.incrementAndGet()
        return stats()
    }

    override fun endpointsInUse(name: String): Map<String, String> = addresses

    override suspend fun reconnect(profile: TunnelProfile) {
        // Recorded before counted: a test that waits for the count then reads the list.
        reconnected += profile.name
        try {
            if (failReconnect) throw IllegalStateException("cannot bring it up")
            addressAfterReconnect?.let { new -> addresses = addresses.mapValues { new } }
        } finally {
            reconnects.incrementAndGet()
        }
    }
}
