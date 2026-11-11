// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn

import com.github.muelli.kabelwacht.vpn.automation.NetworkStateMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Looks hostnames up through the physical network, not the VPN.
 *
 * A plain lookup from this app goes into the tunnel while it is up — and the
 * tunnel is exactly what is in doubt when an endpoint has to be looked up again.
 */
class PhysicalNetworkResolver(private val networks: NetworkStateMonitor) : HostResolver {

    override suspend fun resolve(host: String): List<String> = withContext(Dispatchers.IO) {
        val network = networks.physicalNetwork() ?: throw IOException("no network besides the VPN")
        network.getAllByName(host).mapNotNull { it.hostAddress }
    }
}
