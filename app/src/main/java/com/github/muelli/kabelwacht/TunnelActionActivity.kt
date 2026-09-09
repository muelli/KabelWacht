// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.github.muelli.kabelwacht.data.TunnelProfile

/**
 * Invisible trampoline for one-tap entry points (launcher shortcuts, the
 * home-screen widget): toggles the named tunnel and finishes immediately.
 * Runs the VPN consent flow first when the system has not granted it yet —
 * the one thing a Service could not do, which is why this is an Activity.
 *
 * Exported by necessity (the launcher fires shortcut intents), like the
 * upstream WireGuard app's shortcut activity. The only effect an external
 * caller can trigger is a tunnel toggle, which is announced with a toast.
 */
class TunnelActionActivity : ComponentActivity() {

    private var pending: TunnelProfile? = null

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val profile = pending
        if (result.resultCode == Activity.RESULT_OK && profile != null) {
            toggle(profile, up = true)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val name = intent?.takeIf { it.action == ACTION_TOGGLE }?.getStringExtra(EXTRA_TUNNEL)
        val profile = name?.let { appContainer.repository.get(it) }
        if (profile == null) {
            if (name != null) {
                Toast.makeText(this, getString(R.string.toggle_unknown_tunnel, name), Toast.LENGTH_LONG).show()
            }
            finish()
            return
        }

        val up = appContainer.tunnelManager.activeTunnel.value != profile.name
        val consent = if (up) appContainer.tunnelManager.consentIntent() else null
        if (consent != null) {
            pending = profile
            consentLauncher.launch(consent)
        } else {
            toggle(profile, up)
        }
    }

    private fun toggle(profile: TunnelProfile, up: Boolean) {
        Toast.makeText(
            this,
            getString(if (up) R.string.toggle_connecting else R.string.toggle_disconnecting, profile.name),
            Toast.LENGTH_SHORT,
        ).show()
        // Runs on the application scope: this activity finishes right away.
        appContainer.toggleTunnel(profile.name, up)
        finish()
    }

    companion object {
        const val ACTION_TOGGLE = "com.github.muelli.kabelwacht.action.TOGGLE_TUNNEL"
        const val EXTRA_TUNNEL = "tunnel"
    }
}
