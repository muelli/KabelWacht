// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import com.wireguard.android.backend.Tunnel

/**
 * Minimal [Tunnel] adapter: gives the backend a name and forwards state-change
 * callbacks (invoked by the backend only) to [onState].
 */
class WgTunnel(
    private val tunnelName: String,
    private val onState: (Tunnel.State) -> Unit,
) : Tunnel {

    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        onState(newState)
    }
}
