// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.notification

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.muelli.kabelwacht.vpn.TunnelSource
import com.github.muelli.kabelwacht.vpn.TunnelStatistics
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Which channel each notification goes to, which is what the system settings act on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TunnelNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val dropped = MutableSharedFlow<String>(replay = 1)

    private val tunnels = object : TunnelSource {
        override val activeTunnel = MutableStateFlow<String?>(null)
        override val unexpectedlyDown: SharedFlow<String> = dropped
        override suspend fun statistics(name: String): TunnelStatistics? = null
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    @After
    fun stop() = scope.cancel()

    private val notifier = TunnelNotifier(context, tunnels)

    // The channels of the notifications currently showing.
    private fun showing(): List<String?> = shadowOf(manager).allNotifications.map { it.channelId }

    @Test
    fun the_three_kinds_of_alert_and_the_status_each_have_a_channel_of_their_own() {
        val ids = manager.notificationChannels.map { it.id }

        assertEquals(
            setOf(
                TunnelNotifier.STATUS_CHANNEL_ID,
                TunnelNotifier.ALERTS_CHANNEL_ID,
                TunnelNotifier.DROPPED_CHANNEL_ID,
                TunnelNotifier.SILENT_CHANNEL_ID,
            ),
            ids.toSet(),
        )
        assertEquals("distinct", 4, ids.toSet().size)
    }

    @Test
    fun the_status_is_quiet_and_the_alerts_make_a_sound() {
        fun importance(id: String) = manager.getNotificationChannel(id).importance

        assertEquals(NotificationManager.IMPORTANCE_LOW, importance(TunnelNotifier.STATUS_CHANNEL_ID))
        for (id in listOf(TunnelNotifier.ALERTS_CHANNEL_ID, TunnelNotifier.DROPPED_CHANNEL_ID, TunnelNotifier.SILENT_CHANNEL_ID)) {
            assertEquals(id, NotificationManager.IMPORTANCE_DEFAULT, importance(id))
        }
    }

    @Test
    fun a_silent_peer_is_posted_on_its_own_channel() {
        notifier.peerSilent("home")

        assertEquals(listOf(TunnelNotifier.SILENT_CHANNEL_ID), showing())
    }

    @Test
    fun failures_to_connect_stay_on_the_connection_problems_channel() {
        notifier.autoConnectFailed("a", RuntimeException("no route"))
        notifier.autoConnectFailed("b", null)
        notifier.alwaysOnFailed("c")

        assertEquals(List(3) { TunnelNotifier.ALERTS_CHANNEL_ID }, showing())
    }

    @Test
    fun a_tunnel_stopped_by_the_system_is_posted_on_its_own_channel() {
        dropped.tryEmit("home")
        notifier.observe(scope)

        runBlocking { withTimeout(5_000) { while (showing().isEmpty()) delay(5) } }

        assertEquals(listOf(TunnelNotifier.DROPPED_CHANNEL_ID), showing())
    }

    @Test
    fun the_peer_answering_clears_only_its_own_alert() {
        notifier.peerSilent("home")
        notifier.alwaysOnFailed("home")

        notifier.peerAnswered("home")

        assertEquals(listOf(TunnelNotifier.ALERTS_CHANNEL_ID), showing())
    }

    @Test
    fun clearing_a_peer_that_was_never_reported_is_harmless() {
        notifier.peerAnswered("home")

        assertEquals(emptyList<String?>(), showing())
    }
}
