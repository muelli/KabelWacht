// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.automation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.github.muelli.kabelwacht.MainActivity
import com.github.muelli.kabelwacht.R
import com.github.muelli.kabelwacht.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that keeps network monitoring alive in the background
 * when run conditions are enabled on any tunnel profile.
 */
class ConditionMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startInForeground(getString(R.string.automation_service_notification_title), getString(R.string.automation_service_notification_text))

        val container = appContainer
        monitorJob = scope.launch {
            // Keep service alive only while automations are enabled. The first
            // value decides whether the service survives being started at all,
            // so it has to be the real one — see the seed on
            // AutomationEngine.hasActiveAutomations.
            container.automationEngine.hasActiveAutomations.collect { active ->
                if (!active) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-assert the foreground declaration: if location was granted since
        // the service started, this picks up the location service type (and with
        // it, background SSID access) without a restart.
        startInForeground(
            getString(R.string.automation_service_notification_title),
            getString(R.string.automation_service_notification_text),
        )
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(title: String, text: String) {
        val notification = buildNotification(title, text)
        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val attempts = foregroundServiceTypeAttempts(Build.VERSION.SDK_INT, hasFineLocation)
        for ((index, fgsType) in attempts.withIndex()) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "startForeground(type=$fgsType) refused", e)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException and the
                // ForegroundServiceTypeException family are IllegalStateExceptions.
                Log.w(TAG, "startForeground(type=$fgsType) refused", e)
            }
            if (index == attempts.lastIndex) {
                // Not even the plain declaration is allowed: give up quietly rather
                // than crash; the next foreground visit starts the service again.
                stopSelf()
            }
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.automation_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.automation_notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "tunnel_automation_channel"
        private const val NOTIFICATION_ID = 2001

        private const val TAG = "ConditionMonitor"

        /**
         * The foreground service types to try, most capable first.
         *
         * The location type is what entitles background SSID reads for Wi-Fi
         * rules, but on Android 14+ it is only allowed while the app may use
         * while-in-use location — not when started from BOOT_COMPLETED or
         * restarted by the system in the background (#34). So a declaration
         * with it is followed by one without, which leaves SSID rules seeing an
         * unknown network until the app is next opened and [reassert] runs.
         */
        fun foregroundServiceTypeAttempts(sdkInt: Int, hasFineLocation: Boolean): List<Int> {
            val base = if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            return if (hasFineLocation) {
                listOf(base or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, base)
            } else {
                listOf(base)
            }
        }

        /**
         * Called when the app comes to the foreground: if monitoring runs and
         * location is granted, re-declare the service so it picks up the
         * location type it may have been refused in the background.
         */
        fun reassert(context: Context) {
            val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (context.appContainer.conditionsStore.hasEnabled() && hasFineLocation) sync(context, true)
        }

        fun sync(context: Context, hasAutomations: Boolean) {
            val intent = Intent(context, ConditionMonitorService::class.java)
            if (hasAutomations) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
