// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import com.wireguard.android.backend.Statistics

/**
 * Traffic of the active tunnel, summed over its peers.
 *
 * [lastHandshakeEpochMillis] is the most recent handshake with any peer, or null
 * if none has completed yet (typically a wrong key or an unreachable endpoint).
 * [sampledAtMillis] makes every sample distinct, so a UI showing the handshake
 * age keeps ticking even while the counters stand still.
 */
data class TunnelStatistics(
    val tunnelName: String,
    val rxBytes: Long,
    val txBytes: Long,
    val lastHandshakeEpochMillis: Long?,
    val sampledAtMillis: Long,
) {
    /** Seconds since the last handshake as of [sampledAtMillis], or null if there was none. */
    val handshakeAgeSeconds: Long?
        get() = lastHandshakeEpochMillis?.let { ((sampledAtMillis - it) / 1000).coerceAtLeast(0) }

    companion object {
        fun from(
            tunnelName: String,
            stats: Statistics,
            now: Long = System.currentTimeMillis(),
        ): TunnelStatistics {
            val latest = stats.peers().maxOfOrNull { stats.peer(it)?.latestHandshakeEpochMillis() ?: 0L } ?: 0L
            return TunnelStatistics(
                tunnelName = tunnelName,
                rxBytes = stats.totalRx(),
                txBytes = stats.totalTx(),
                lastHandshakeEpochMillis = latest.takeIf { it > 0 },
                sampledAtMillis = now,
            )
        }
    }
}
