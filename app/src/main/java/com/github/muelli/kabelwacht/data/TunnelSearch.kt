// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import com.wireguard.config.Config

/**
 * Case-insensitive tunnel search. Name matches rank first (prefix before
 * substring), then tunnels whose configuration data matches: peer endpoints
 * (host names), DNS servers and search domains, interface addresses, and
 * allowed IPs. Key material is deliberately never searched. Within a rank the
 * repository's alphabetical order is preserved (the sort is stable).
 */
object TunnelSearch {

    fun filter(profiles: List<TunnelProfile>, query: String): List<TunnelProfile> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return profiles
        return profiles
            .mapNotNull { profile -> rank(profile, q)?.let { profile to it } }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun rank(profile: TunnelProfile, q: String): Int? {
        val name = profile.name.lowercase()
        return when {
            name.startsWith(q) -> 0
            q in name -> 1
            matchesConfigData(profile.config, q) -> 2
            else -> null
        }
    }

    private fun matchesConfigData(config: Config, q: String): Boolean {
        val itf = config.getInterface()
        val haystack = buildList {
            config.peers.forEach { peer ->
                peer.endpoint.map { it.toString() }.orElse(null)?.let(::add)
                peer.allowedIps.forEach { add(it.toString()) }
            }
            itf.addresses.forEach { add(it.toString()) }
            itf.dnsServers.mapNotNull { it.hostAddress }.forEach(::add)
            addAll(itf.dnsSearchDomains)
        }
        return haystack.any { q in it.lowercase() }
    }
}
