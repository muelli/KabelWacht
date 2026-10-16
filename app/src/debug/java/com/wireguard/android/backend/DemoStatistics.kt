// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

// Lives in WireGuard's package because Statistics can only be built from there.
package com.wireguard.android.backend

import com.wireguard.crypto.Key

/** Per-peer counters for [demoStatistics]. */
data class DemoPeerStats(val key: Key, val rxBytes: Long, val txBytes: Long, val latestHandshakeEpochMillis: Long)

/** Build a [Statistics] the way a real backend would report it. */
fun demoStatistics(peers: List<DemoPeerStats>): Statistics = Statistics().apply {
    peers.forEach { add(it.key, it.rxBytes, it.txBytes, it.latestHandshakeEpochMillis) }
}
