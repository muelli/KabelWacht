// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** What the notification layer needs to know about tunnels. */
interface TunnelSource {

    /** Name of the tunnel currently up, or null. */
    val activeTunnel: StateFlow<String?>

    /** Name of a tunnel that went down without this app asking for it. */
    val unexpectedlyDown: SharedFlow<String>

    /** Current traffic of [name], or null if it is not the active tunnel. */
    suspend fun statistics(name: String): TunnelStatistics?
}
