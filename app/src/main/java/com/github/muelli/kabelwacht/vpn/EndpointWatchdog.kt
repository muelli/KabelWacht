// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import android.os.SystemClock
import android.util.Log
import com.github.muelli.kabelwacht.data.TunnelProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Resolves a hostname to the addresses it points to right now, as text. */
interface HostResolver {
    suspend fun resolve(host: String): List<String>
}

/** What [EndpointWatchdog] needs from the tunnel layer. */
interface EndpointTunnels : TunnelSource {

    /**
     * The peer endpoints of [name] that are hostnames, each with the address the
     * tunnel was brought up with. Empty for a tunnel that is not up, or that only
     * has literal addresses as endpoints.
     */
    fun endpointsInUse(name: String): Map<String, String>

    /** Takes [profile] down and up again, resolving its endpoints afresh. */
    suspend fun reconnect(profile: TunnelProfile)
}

/**
 * Brings a tunnel back whose peer moved to another address.
 *
 * WireGuard resolves an endpoint's hostname once, when the tunnel comes up. If
 * the peer's address changes after that — or the dynamic DNS record still held
 * the old one at that moment — the handshake goes to the wrong address for as
 * long as the tunnel stays up, and nothing fails visibly.
 *
 * While a tunnel with a hostname endpoint looks stalled (see [StallTracker]),
 * this looks the hostname up again, through the physical network because the
 * tunnel itself is what is not working. Only if the address the tunnel is using
 * is no longer among the answers is the tunnel reconnected: a peer that is
 * merely down is looked up at ever longer intervals, but never restarted.
 */
class EndpointWatchdog(
    private val tunnels: EndpointTunnels,
    private val profiles: (String) -> TunnelProfile?,
    private val resolver: HostResolver,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val pollMillis: Long = POLL_MILLIS,
    private val resolveTimeoutMillis: Long = RESOLVE_TIMEOUT_MILLIS,
    private val newTracker: () -> StallTracker = { StallTracker() },
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
        while (true) {
            delay(pollMillis)
            // Tunnels whose endpoints are literal addresses have nothing to re-resolve.
            if (tunnels.endpointsInUse(name).isEmpty()) continue
            val stats = tunnels.statistics(name) ?: continue
            if (!tracker.onSample(stats, now())) continue

            if (endpointMoved(name)) {
                val profile = profiles(name)
                if (profile != null) {
                    Log.i(TAG, "$name: its endpoint now resolves elsewhere; reconnecting")
                    try {
                        tunnels.reconnect(profile)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "$name: reconnecting failed", e)
                    }
                    tracker.reset()
                    continue
                }
            }
            tracker.checkedWithoutChange(now())
        }
    }

    // True if some hostname endpoint no longer includes the address in use.
    // A failed or empty lookup says nothing, so it never counts as a move.
    private suspend fun endpointMoved(name: String): Boolean {
        for ((host, inUse) in tunnels.endpointsInUse(name)) {
            val answers = withTimeoutOrNull(resolveTimeoutMillis) {
                try {
                    resolver.resolve(host)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            if (!answers.isNullOrEmpty() && inUse !in answers) return true
        }
        return false
    }

    companion object {
        private const val TAG = "KabelWacht"
        const val POLL_MILLIS = 5_000L
        const val RESOLVE_TIMEOUT_MILLIS = 15_000L
    }
}

/**
 * Decides from a tunnel's statistics whether it is stuck — sending without
 * ever being answered — and, if so, when to look at its endpoint again.
 *
 * Stuck means one of:
 *  - no handshake yet although something was sent (the handshake attempts
 *    themselves count), or
 *  - the last handshake is older than [staleHandshakeSeconds] although more has
 *    been sent since. A healthy tunnel that is sending renews its handshake
 *    every two minutes; an idle one sends nothing and is left alone.
 *
 * The condition has to hold for [stallGraceMillis] first, so that the traffic
 * that restarts an idle tunnel, whose handshake takes a moment, does not count.
 * After a check that found nothing to do, the next one waits longer each time
 * ([backoffMillis]), until a handshake shows that the tunnel works.
 */
class StallTracker(
    private val stallGraceMillis: Long = STALL_GRACE_MILLIS,
    private val staleHandshakeSeconds: Long = STALE_HANDSHAKE_SECONDS,
    private val backoffMillis: List<Long> = BACKOFF_MILLIS,
) {
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastHandshake: Long? = null
    private var txAtHandshake = 0L
    private var stalledSince: Long? = null
    private var nextCheckAt = 0L
    private var step = 0

    init {
        require(backoffMillis.isNotEmpty())
    }

    /** True when the tunnel looks stuck and it is time to check its endpoint. [now] is monotonic. */
    fun onSample(stats: TunnelStatistics, now: Long): Boolean {
        // Counters that went back to zero belong to a tunnel that was started anew.
        if (stats.rxBytes < lastRx || stats.txBytes < lastTx) reset()
        lastRx = stats.rxBytes
        lastTx = stats.txBytes

        if (stats.lastHandshakeEpochMillis != lastHandshake) {
            lastHandshake = stats.lastHandshakeEpochMillis
            txAtHandshake = stats.txBytes
            if (lastHandshake != null) {
                step = 0
                nextCheckAt = 0
            }
        }

        val age = stats.handshakeAgeSeconds
        val stuck = if (age == null) {
            stats.txBytes > 0
        } else {
            age >= staleHandshakeSeconds && stats.txBytes > txAtHandshake
        }
        if (!stuck) {
            stalledSince = null
            return false
        }
        val since = stalledSince ?: now.also { stalledSince = it }
        return now - since >= stallGraceMillis && now >= nextCheckAt
    }

    /** The check found the endpoint where it was: look again later, and less often. */
    fun checkedWithoutChange(now: Long) {
        nextCheckAt = now + backoffMillis[minOf(step, backoffMillis.lastIndex)]
        step++
    }

    /** Forget everything: the tunnel was restarted. */
    fun reset() {
        lastRx = 0
        lastTx = 0
        lastHandshake = null
        txAtHandshake = 0
        stalledSince = null
        nextCheckAt = 0
        step = 0
    }

    companion object {
        const val STALL_GRACE_MILLIS = 45_000L

        // WireGuard renews a session after two minutes and drops it after three.
        const val STALE_HANDSHAKE_SECONDS = 200L

        val BACKOFF_MILLIS = listOf(30_000L, 60_000L, 120_000L, 300_000L, 600_000L)
    }
}
