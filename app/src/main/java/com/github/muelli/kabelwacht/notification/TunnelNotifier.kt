// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.github.muelli.kabelwacht.MainActivity
import com.github.muelli.kabelwacht.R
import com.github.muelli.kabelwacht.util.TrafficFormat
import com.github.muelli.kabelwacht.vpn.TunnelSource
import com.github.muelli.kabelwacht.vpn.TunnelStatistics
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The app's own notifications, apart from the automation service's:
 *
 * - an ongoing **status** notification while a tunnel is up — its small icon is
 *   what tells, from the status bar, that the VPN is on — with live traffic and
 *   a Disconnect action;
 * - **alerts** when a tunnel could not be brought up without the user watching
 *   (automation, always-on) or was stopped by the system or another VPN app.
 *
 * Posted through the platform [NotificationManager], which silently drops them
 * while the user has not granted notification permission.
 */
class TunnelNotifier(
    context: Context,
    private val tunnels: TunnelSource,
) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    private val power = this.context.getSystemService(PowerManager::class.java)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    STATUS_CHANNEL_ID,
                    this.context.getString(R.string.status_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = this@TunnelNotifier.context.getString(R.string.status_notification_channel_desc)
                    setShowBadge(false)
                },
                NotificationChannel(
                    ALERTS_CHANNEL_ID,
                    this.context.getString(R.string.alerts_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = this@TunnelNotifier.context.getString(R.string.alerts_notification_channel_desc)
                },
            ),
        )
    }

    // Tunnels whose automated connection failure was already reported.
    private val autoConnectReported: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Keep the status notification in step with the active tunnel, on [scope]. */
    fun observe(scope: CoroutineScope) {
        scope.launch {
            tunnels.activeTunnel.collectLatest { name ->
                if (name == null) {
                    manager.cancel(STATUS_NOTIFICATION_ID)
                    return@collectLatest
                }
                // Connected after all: whatever failure was reported is stale.
                autoConnectReported.remove(name)
                manager.cancel(name, ALERT_NOTIFICATION_ID)
                manager.notify(STATUS_NOTIFICATION_ID, statusNotification(name, null))
                // Refresh only while the screen is on.
                screenOn().collectLatest { on ->
                    while (on) {
                        // Re-posting also brings the notification back if it was swiped away
                        // (Android 14+ lets users dismiss even ongoing notifications).
                        manager.notify(STATUS_NOTIFICATION_ID, statusNotification(name, tunnels.statistics(name)))
                        delay(STATUS_REFRESH_MILLIS)
                    }
                }
            }
        }
        scope.launch {
            tunnels.unexpectedlyDown.collect { name ->
                alert(name, context.getString(R.string.alert_tunnel_dropped))
            }
        }
    }

    /** An automated connection of [name] failed; a null [cause] means VPN consent is missing. */
    fun autoConnectFailed(
        name: String,
        cause: Throwable?,
    ) {
        if (!autoConnectReported.add(name)) return
        alert(
            name,
            if (cause == null) {
                context.getString(R.string.alert_no_vpn_consent)
            } else {
                context.getString(R.string.alert_auto_connect_failed, cause.reason())
            },
        )
    }

    /** Always-on VPN could not bring [name] up, even after retrying. */
    fun alwaysOnFailed(name: String) {
        alert(
            name,
            context.getString(R.string.always_on_failed_text, name),
            title = context.getString(R.string.always_on_failed_title),
        )
    }

    /** Whether the screen is on, now and on every change. */
    private fun screenOn(): Flow<Boolean> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        trySend(intent.action == Intent.ACTION_SCREEN_ON)
                    }
                }
            context.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
            )
            trySend(power.isInteractive)
            awaitClose { context.unregisterReceiver(receiver) }
        }.distinctUntilChanged()

    private fun Throwable.reason(): String = message ?: context.getString(R.string.error_tunnel_state)

    private fun alert(
        name: String,
        text: String,
        title: String = context.getString(R.string.alert_title, name),
    ) {
        val notification =
            NotificationCompat
                .Builder(context, ALERTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_tunnel)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build()
        // One alert per tunnel, replaced rather than stacked.
        manager.notify(name, ALERT_NOTIFICATION_ID, notification)
    }

    private fun statusNotification(
        name: String,
        stats: TunnelStatistics?,
    ): Notification {
        val disconnect =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(NotificationActionReceiver.ACTION_DISCONNECT)
                    .putExtra(NotificationActionReceiver.EXTRA_TUNNEL, name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        // The lock screen only learns that a VPN is up, not which one.
        val public =
            NotificationCompat
                .Builder(context, STATUS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_tunnel)
                .setContentTitle(context.getString(R.string.status_notification_public))
                .build()
        return NotificationCompat
            .Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_tunnel)
            .setContentTitle(context.getString(R.string.status_notification_title, name))
            .setContentText(stats?.let { statisticsLine(context, it) })
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.status_notification_disconnect), disconnect)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val STATUS_CHANNEL_ID = "tunnel_status_channel"
        const val ALERTS_CHANNEL_ID = "tunnel_alerts_channel"
        private const val STATUS_NOTIFICATION_ID = 2002
        private const val ALERT_NOTIFICATION_ID = 2003
        private const val STATUS_REFRESH_MILLIS = 2000L

        /** "↓ 1.2 MiB · ↑ 340 KiB · handshake 12 s ago", shared with the tunnel list. */
        fun statisticsLine(
            context: Context,
            stats: TunnelStatistics,
        ): String {
            val traffic =
                context.getString(
                    R.string.stats_traffic,
                    TrafficFormat.bytes(stats.rxBytes),
                    TrafficFormat.bytes(stats.txBytes),
                )
            val handshake =
                stats.handshakeAgeSeconds
                    ?.let { context.getString(R.string.stats_handshake, TrafficFormat.duration(it)) }
                    ?: context.getString(R.string.stats_no_handshake)
            return "$traffic · $handshake"
        }
    }
}
