// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

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
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PeerSilenceMonitorTest {

    private val grace = 10_000L

    private val scope = CoroutineScope(Dispatchers.Default)

    @After
    fun stop() = scope.cancel()

    // The monitor's clock, which the tests move by hand.
    private val clock = AtomicLong(1_000)

    private val samples = AtomicInteger()
    private val silent = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val answered = java.util.Collections.synchronizedList(mutableListOf<String>())

    private val active = MutableStateFlow<String?>(null)

    // Sent, never answered.
    @Volatile private var stats: () -> TunnelStatistics = { TunnelStatistics("a", 0, 296, null, 0) }

    private val healthy: () -> TunnelStatistics = {
        val at = System.currentTimeMillis()
        TunnelStatistics("a", 900, 1_200, at - 5_000, at)
    }

    private val tunnels = object : TunnelSource {
        override val activeTunnel = active
        override val unexpectedlyDown: SharedFlow<String> = MutableSharedFlow()
        override suspend fun statistics(name: String): TunnelStatistics? {
            samples.incrementAndGet()
            return stats()
        }
    }

    private fun start() {
        PeerSilenceMonitor(
            tunnels = tunnels,
            onSilent = { silent += it },
            onAnswered = { answered += it },
            now = clock::get,
            pollMillis = 5,
            newTracker = { StallTracker(stallGraceMillis = grace) },
        ).observe(scope)
        active.value = "a"
    }

    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(5) }
    }

    // Lets the monitor take a few more samples, so that "nothing happened" means it looked.
    private fun letItLook() {
        val before = samples.get()
        waitFor("more samples") { samples.get() >= before + 5 }
    }

    @Test
    fun a_peer_that_stays_silent_is_reported_once_after_the_grace_period() {
        start()
        waitFor("a first sample") { samples.get() >= 1 }
        letItLook()
        assertEquals("not yet", emptyList<String>(), silent.toList())

        clock.addAndGet(grace - 1_000)
        letItLook()
        assertEquals("still within the grace period", emptyList<String>(), silent.toList())

        clock.addAndGet(1_000)
        waitFor("the report") { silent.isNotEmpty() }
        letItLook()

        assertEquals(listOf("a"), silent.toList())
        assertEquals(emptyList<String>(), answered.toList())
    }

    @Test
    fun a_healthy_tunnel_is_never_reported() {
        stats = healthy

        start()
        letItLook()
        clock.addAndGet(grace * 10)
        letItLook()

        assertEquals(emptyList<String>(), silent.toList())
        assertEquals(emptyList<String>(), answered.toList())
    }

    @Test
    fun an_idle_tunnel_that_never_sent_anything_is_never_reported() {
        stats = { TunnelStatistics("a", 0, 0, null, 0) }

        start()
        clock.addAndGet(grace * 10)
        letItLook()

        assertEquals(emptyList<String>(), silent.toList())
    }

    @Test
    fun a_peer_that_answers_after_all_clears_the_report() {
        start()
        letItLook()
        clock.addAndGet(grace)
        waitFor("the report") { silent.isNotEmpty() }

        stats = healthy
        waitFor("the all-clear") { answered.isNotEmpty() }
        letItLook()

        assertEquals(listOf("a"), silent.toList())
        assertEquals(listOf("a"), answered.toList())
    }

    @Test
    fun a_peer_that_goes_silent_again_is_reported_again() {
        start()
        letItLook()
        clock.addAndGet(grace)
        waitFor("the first report") { silent.size >= 1 }
        stats = healthy
        waitFor("the all-clear") { answered.size >= 1 }

        // A fresh handshake that then goes stale while more is sent.
        val handshakeAt = System.currentTimeMillis() - 5_000
        var sent = 1_200L
        stats = {
            sent += 100
            TunnelStatistics("a", 900, sent, handshakeAt, handshakeAt + 400_000)
        }
        letItLook()
        clock.addAndGet(grace)
        waitFor("the second report") { silent.size >= 2 }
        letItLook()

        assertEquals(listOf("a", "a"), silent.toList())
    }

    @Test
    fun switching_the_tunnel_off_clears_the_report() {
        start()
        letItLook()
        clock.addAndGet(grace)
        waitFor("the report") { silent.isNotEmpty() }

        active.value = null
        waitFor("the all-clear") { answered.isNotEmpty() }

        assertEquals(listOf("a"), answered.toList())
    }

    @Test
    fun switching_off_a_tunnel_that_was_not_reported_says_nothing() {
        stats = healthy

        start()
        letItLook()
        active.value = null
        // Nothing is sampled any more once it is off, so there is nothing to wait for.
        Thread.sleep(100)

        assertEquals(emptyList<String>(), answered.toList())
    }
}
