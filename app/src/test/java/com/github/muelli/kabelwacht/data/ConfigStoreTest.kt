// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import com.wireguard.config.BadConfigException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the wg-quick parsing/serialization used for import and storage. The
 * WireGuard config classes are pure JVM code, so this runs as a fast local test.
 */
class ConfigStoreTest {

    // Example keys taken from the WireGuard documentation (valid 32-byte base64).
    private val validConfig = """
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
    fun parse_then_serialize_is_idempotent() {
        val once = ConfigStore.parse(validConfig)
        val twice = ConfigStore.parse(once.toWgQuickString())

        // Re-parsing the serialized form must yield an identical canonical config.
        assertEquals(once.toWgQuickString(), twice.toWgQuickString())
    }

    @Test
    fun parsed_config_preserves_peer_endpoint() {
        val config = ConfigStore.parse(validConfig)
        val endpoint = config.peers.single().endpoint.orElse(null)?.toString()
        assertTrue("expected endpoint host:port, was $endpoint", endpoint?.contains("192.95.5.69") == true)
    }

    @Test
    fun invalid_config_throws_BadConfigException() {
        val invalid = """
            [Interface]
            PrivateKey = definitely-not-a-valid-key
        """.trimIndent()

        assertThrows(BadConfigException::class.java) {
            ConfigStore.parse(invalid)
        }
    }
}
