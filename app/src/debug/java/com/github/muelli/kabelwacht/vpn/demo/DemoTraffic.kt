// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.demo

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * Plausible, deterministic traffic for a demo tunnel: a first handshake shortly
 * after connecting, a re-handshake every two minutes as WireGuard does, and
 * counters that only ever grow, at a rate that swells and ebbs so live numbers
 * on screen look alive.
 */
object DemoTraffic {
    const val FIRST_HANDSHAKE_MS = 800L
    const val REKEY_MS = 120_000L

    // Received bytes per millisecond: a base rate plus a 20-second swell.
    private const val BASE_RATE = 30.0
    private const val SWELL_RATE = 25.0
    private const val SWELL_PERIOD_MS = 20_000.0

    data class Sample(val rxBytes: Long, val txBytes: Long, val latestHandshakeEpochMillis: Long)

    /**
     * Counters [elapsedMs] after a tunnel came up at [startEpochMillis], for its
     * [peerIndex]-th peer (later peers carry less traffic).
     */
    fun sample(startEpochMillis: Long, elapsedMs: Long, peerIndex: Int = 0): Sample {
        val t = elapsedMs.coerceAtLeast(0).toDouble()
        val w = 2 * PI / SWELL_PERIOD_MS
        // Integral of BASE + SWELL * (1 - cos(w t)): non-decreasing by construction.
        val rx = (BASE_RATE * t + SWELL_RATE * (t - sin(w * t) / w)) / (peerIndex + 1)
        val handshake = if (elapsedMs < FIRST_HANDSHAKE_MS) {
            0L
        } else {
            val sinceFirst = elapsedMs - FIRST_HANDSHAKE_MS
            startEpochMillis + FIRST_HANDSHAKE_MS + floor(sinceFirst.toDouble() / REKEY_MS).toLong() * REKEY_MS
        }
        return Sample(rxBytes = rx.toLong(), txBytes = (rx / 6).toLong(), latestHandshakeEpochMillis = handshake)
    }
}
