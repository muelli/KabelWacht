// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTrafficTest {

    private val start = 1_700_000_000_000L

    @Test
    fun counters_never_decrease() {
        var previous = DemoTraffic.sample(start, 0)
        for (ms in 250L..600_000L step 250) {
            val s = DemoTraffic.sample(start, ms)
            assertTrue("rx went down at $ms", s.rxBytes >= previous.rxBytes)
            assertTrue("tx went down at $ms", s.txBytes >= previous.txBytes)
            previous = s
        }
        assertTrue(previous.rxBytes > 0)
    }

    @Test
    fun no_handshake_right_after_connecting() {
        assertEquals(0L, DemoTraffic.sample(start, DemoTraffic.FIRST_HANDSHAKE_MS - 1).latestHandshakeEpochMillis)
    }

    @Test
    fun handshake_renews_every_two_minutes() {
        val first = start + DemoTraffic.FIRST_HANDSHAKE_MS
        assertEquals(first, DemoTraffic.sample(start, DemoTraffic.FIRST_HANDSHAKE_MS).latestHandshakeEpochMillis)
        assertEquals(first, DemoTraffic.sample(start, 100_000).latestHandshakeEpochMillis)
        assertEquals(
            first + DemoTraffic.REKEY_MS,
            DemoTraffic.sample(start, DemoTraffic.FIRST_HANDSHAKE_MS + DemoTraffic.REKEY_MS).latestHandshakeEpochMillis,
        )
    }

    @Test
    fun later_peers_carry_less_traffic() {
        assertTrue(DemoTraffic.sample(start, 60_000, 1).rxBytes < DemoTraffic.sample(start, 60_000, 0).rxBytes)
    }
}
