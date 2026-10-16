// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend

/**
 * The WireGuard backend for this build. Release builds always use the real
 * userspace implementation; the debug build's counterpart can swap in a demo
 * backend, which therefore never ships in a release APK.
 */
object Backends {
    fun create(context: Context): Backend = GoBackend(context.applicationContext)
}
