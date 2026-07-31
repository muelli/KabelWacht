// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import com.wireguard.config.Config

/**
 * A stored WireGuard profile: a user-visible [name] and its parsed [config].
 *
 * The name doubles as the on-disk filename (`<name>.conf`) and as the network
 * interface name once the tunnel is up, so it must satisfy
 * [com.wireguard.android.backend.Tunnel]'s naming rules.
 */
data class TunnelProfile(
    val name: String,
    val config: Config,
)
