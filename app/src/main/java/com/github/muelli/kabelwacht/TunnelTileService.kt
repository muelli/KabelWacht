// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile toggling the same tunnel the widget shows: the active
 * one, else the most recently used, else the first profile. Toggles in place
 * when VPN consent is already granted; otherwise it launches the
 * [TunnelActionActivity] trampoline (collapsing the shade) to run the consent
 * flow. On a locked device the toggle runs after unlocking — silently
 * dropping the VPN from the lock screen would be a downgrade attack surface.
 */
class TunnelTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listening: Job? = null

    override fun onStartListening() {
        // Re-render while the shade is open so a toggle is reflected promptly.
        listening = scope.launch {
            appContainer.tunnelManager.activeTunnel.collect { render() }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        val target = appContainer.quickToggleState().targetName ?: return
        if (isLocked) {
            unlockAndRun { toggle(target) }
        } else {
            toggle(target)
        }
    }

    private fun toggle(name: String) {
        val container = appContainer
        val up = container.tunnelManager.activeTunnel.value != name
        if (up && !container.tunnelManager.hasConsent()) {
            // Consent needs an Activity result — hand over to the trampoline.
            val intent = Intent(this, TunnelActionActivity::class.java)
                .setAction(TunnelActionActivity.ACTION_TOGGLE)
                .putExtra(TunnelActionActivity.EXTRA_TUNNEL, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } else {
            container.toggleTunnel(name, up)
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val state = appContainer.quickToggleState()
        val target = state.targetName
        if (target == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.app_name)
            tile.subtitle = getString(R.string.widget_no_tunnel)
        } else {
            val active = state.activeName == target
            tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = target
            tile.subtitle = getString(
                if (active) R.string.widget_connected else R.string.widget_tap_to_connect,
            )
        }
        tile.updateTile()
    }
}
