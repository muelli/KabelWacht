// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Tells when a tunnel that is up does not get through.
 *
 * WireGuard counts a tunnel as up once its interface exists, whether or not the
 * peer ever answers. A blocked or dead peer therefore leaves the toggle on and
 * Android's key symbol showing, and with lockdown nothing else gets out either:
 * the failure is silent. This watches the active tunnel and calls [onSilent],
 * once, when it has been sending without being answered (see [StallTracker]) for
 * [SILENCE_GRACE_MILLIS], and [onAnswered] when that is over: the peer answered
 * after all, or the tunnel was switched off.
 *
 * It only looks at the statistics, so it holds for every kind of endpoint.
 */
class PeerSilenceMonitor(
    private val tunnels: TunnelSource,
    private val onSilent: (String) -> Unit,
    private val onAnswered: (String) -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val pollMillis: Long = POLL_MILLIS,
    private val newTracker: () -> StallTracker = { StallTracker(stallGraceMillis = SILENCE_GRACE_MILLIS) },
) {

    /** Watches whichever tunnel is up, for as long as [scope] lives. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(scope: CoroutineScope): Job = scope.launch {
        tunnels.activeTunnel.collectLatest { name ->
            if (name != null) watch(name)
        }
    }

    internal suspend fun watch(name: String) {
        val tracker = newTracker()
        var reported = false
        try {
            while (true) {
                delay(pollMillis)
                val stats = tunnels.statistics(name) ?: continue
                tracker.onSample(stats, now())
                val silent = tracker.isStalled(now())
                if (silent && !reported) {
                    reported = true
                    onSilent(name)
                } else if (!silent && reported) {
                    reported = false
                    onAnswered(name)
                }
            }
        } finally {
            // Switched off, or another tunnel took over: the report is stale.
            if (reported) onAnswered(name)
        }
    }

    companion object {
        const val POLL_MILLIS = 10_000L

        // WireGuard retries an unanswered handshake every five seconds: this is a dozen
        // attempts, enough not to cry wolf over a slow start.
        const val SILENCE_GRACE_MILLIS = 60_000L
    }
}
