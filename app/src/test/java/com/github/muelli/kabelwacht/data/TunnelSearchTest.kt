// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelSearchTest {

    private fun profile(name: String, endpoint: String? = null, dns: String? = null) = TunnelProfile(
        name,
        ConfigStore.parse(
            buildString {
                appendLine("[Interface]")
                appendLine("PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=")
                appendLine("Address = 10.0.0.2/32")
                if (dns != null) appendLine("DNS = $dns")
                appendLine()
                appendLine("[Peer]")
                appendLine("PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=")
                appendLine("AllowedIPs = 192.168.77.0/24")
                if (endpoint != null) appendLine("Endpoint = $endpoint")
            },
        ),
    )

    private val home = profile("home", endpoint = "vpn.example.org:51820")
    private val office = profile("office", endpoint = "gw.example.com:443", dns = "9.9.9.9")
    private val homework = profile("work-at-home", endpoint = "10.11.12.13:51820")

    private val all = listOf(home, homework, office)

    @Test
    fun empty_query_returns_everything_unchanged() {
        assertEquals(all, TunnelSearch.filter(all, ""))
        assertEquals(all, TunnelSearch.filter(all, "   "))
    }

    @Test
    fun name_prefix_ranks_before_name_substring() {
        // "home" is a prefix of "home", a substring of "work-at-home".
        assertEquals(listOf(home, homework), TunnelSearch.filter(all, "home"))
    }

    @Test
    fun name_matches_rank_before_config_matches() {
        // "org" matches nothing by name except... nothing; but "o" matches names.
        // Use "office": name match; "example" matches only config data.
        val byName = TunnelSearch.filter(all, "office")
        assertEquals(listOf(office), byName)

        val byConfig = TunnelSearch.filter(all, "example")
        assertEquals(listOf(home, office), byConfig)
    }

    @Test
    fun endpoint_host_matches_secondarily() {
        assertEquals(listOf(office), TunnelSearch.filter(all, "gw.example.com"))
        // Name match for the same query string ranks first.
        val mixed = TunnelSearch.filter(all, "work")
        assertEquals(listOf(homework), mixed)
    }

    @Test
    fun dns_and_allowed_ips_match() {
        assertEquals(listOf(office), TunnelSearch.filter(all, "9.9.9.9"))
        // AllowedIPs shared by all three profiles.
        assertEquals(all, TunnelSearch.filter(all, "192.168.77."))
    }

    @Test
    fun search_is_case_insensitive() {
        assertEquals(listOf(office), TunnelSearch.filter(all, "OFFICE"))
        assertEquals(listOf(office), TunnelSearch.filter(all, "GW.EXAMPLE"))
    }

    @Test
    fun key_material_is_never_matched() {
        // Substring of the private/public keys used above.
        assertEquals(emptyList<TunnelProfile>(), TunnelSearch.filter(all, "yAnz5TF"))
        assertEquals(emptyList<TunnelProfile>(), TunnelSearch.filter(all, "xTIBA5"))
    }

    @Test
    fun no_match_returns_empty() {
        assertEquals(emptyList<TunnelProfile>(), TunnelSearch.filter(all, "zzz-nowhere"))
    }
}
