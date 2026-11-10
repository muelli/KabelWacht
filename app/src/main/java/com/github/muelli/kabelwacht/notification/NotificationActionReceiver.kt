// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.muelli.kabelwacht.appContainer

/**
 * Handles the status notification's Disconnect action. Not exported: only the
 * app's own (immutable) PendingIntent reaches it.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_DISCONNECT) return
        val name = intent.getStringExtra(EXTRA_TUNNEL) ?: return
        // A manual toggle, so automation does not bring it straight back up. Runs
        // on the application scope; the VPN service keeps the process alive.
        context.appContainer.toggleTunnel(name, up = false)
    }

    companion object {
        const val ACTION_DISCONNECT = "com.github.muelli.kabelwacht.action.DISCONNECT"
        const val EXTRA_TUNNEL = "tunnel"
    }
}
