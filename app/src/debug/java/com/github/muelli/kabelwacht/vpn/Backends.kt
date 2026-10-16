// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import android.content.Context
import com.github.muelli.kabelwacht.vpn.demo.DemoBackend
import com.github.muelli.kabelwacht.vpn.demo.DemoVpn
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import java.io.File

/**
 * The WireGuard backend for this build. Debug builds use [DemoBackend] while
 * the marker file `files/demo-mode` exists, so screenshots and videos can show
 * connected tunnels without a server. There is deliberately no UI for it:
 *
 *     adb shell run-as com.github.muelli.kabelwacht touch files/demo-mode
 *
 * then restart the app (`am force-stop`). Release builds never contain this.
 */
object Backends {
    const val DEMO_MARKER = "demo-mode"

    fun create(context: Context): Backend {
        val app = context.applicationContext
        return if (File(app.filesDir, DEMO_MARKER).exists()) DemoBackend(DemoVpn.System(app)) else GoBackend(app)
    }
}
