// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.demo

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Holds the empty VPN interface for [DemoBackend]: an address and no routes,
 * so no traffic is diverted, yet Android shows the VPN key icon and treats a
 * VPN as connected, as it would for a real tunnel.
 */
class DemoVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_OPEN -> {
                tun?.close()
                tun = runCatching {
                    Builder()
                        .setSession(intent.getStringExtra(EXTRA_SESSION) ?: "demo")
                        .addAddress(
                            intent.getStringExtra(EXTRA_ADDRESS) ?: "10.0.0.2",
                            intent.getIntExtra(EXTRA_PREFIX, 32),
                        )
                        .establish()
                }.onFailure { Log.w(TAG, "Could not open the demo VPN interface", it) }.getOrNull()
                if (tun == null) stopSelf(startId)
            }
            else -> {
                tun?.close()
                tun = null
                // Only if no later request (an open) has arrived meanwhile.
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        tun?.close()
        tun = null
        stopSelf()
    }

    override fun onDestroy() {
        tun?.close()
        tun = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DemoVpnService"
        private const val ACTION_OPEN = "open"
        private const val ACTION_CLOSE = "close"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_PREFIX = "prefix"

        fun open(context: Context, session: String, address: String, prefixLength: Int) {
            context.startService(
                Intent(context, DemoVpnService::class.java)
                    .setAction(ACTION_OPEN)
                    .putExtra(EXTRA_SESSION, session)
                    .putExtra(EXTRA_ADDRESS, address)
                    .putExtra(EXTRA_PREFIX, prefixLength),
            )
        }

        fun close(context: Context) {
            context.startService(Intent(context, DemoVpnService::class.java).setAction(ACTION_CLOSE))
        }
    }
}
