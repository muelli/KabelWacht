// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure-JVM tests for the repository's import helpers: the canonical-form
 * comparison behind duplicate detection, and auto-generated name suggestion.
 */
class TunnelRepositoryTest {

    private val config = """
        [Interface]
        PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
        Address = 10.0.0.2/32
        DNS = 1.1.1.1

        [Peer]
        PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
        Endpoint = 192.95.5.69:51820
        AllowedIPs = 0.0.0.0/0
    """.trimIndent()

    @Test
    fun reformatted_config_is_the_same_canonical_config() {
        // Same settings, different formatting/whitespace — must canonicalize equal,
        // so a re-scan of the same QR code is recognized as a duplicate.
        val reformatted = config.replace(" = ", "=").plus("\n")
        assertEquals(
            ConfigStore.parse(config).toWgQuickString(),
            ConfigStore.parse(reformatted).toWgQuickString(),
        )
    }

    @Test
    fun changed_endpoint_is_not_a_duplicate() {
        val other = config.replace("192.95.5.69", "192.95.5.70")
        assertNotEquals(
            ConfigStore.parse(config).toWgQuickString(),
            ConfigStore.parse(other).toWgQuickString(),
        )
    }

    @Test
    fun suggests_first_free_name() {
        assertEquals("wg-tunnel-1", TunnelRepository.suggestName { false })
        assertEquals(
            "wg-tunnel-3",
            TunnelRepository.suggestName { it in setOf("wg-tunnel-1", "wg-tunnel-2") },
        )
    }

    @Test
    fun suggested_names_are_valid_tunnel_names() {
        // 15-char cap: names double as the network interface name.
        val name = TunnelRepository.suggestName { taken -> taken.removePrefix("wg-tunnel-").toInt() < 100 }
        assertEquals("wg-tunnel-100", name)
        assert(!com.wireguard.android.backend.Tunnel.isNameInvalid(name))
    }
}
