// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicKeysTest {

    // RFC 7748 section 6.1 X25519 test vector (Alice).
    private val alicePrivate = "dwdtCnMYpX08FsFyUbJmRd9ML4frwJkqsXf7pR25LCo="
    private val alicePublic = "hSDwCYkwp1R0i33ctD73Wg2/Og0mOBr066SpjqqbTmo="

    @Test
    fun derivesKnownPublicKey() {
        assertEquals(alicePublic, derivePublicKey(alicePrivate))
    }

    @Test
    fun ignoresSurroundingWhitespace() {
        assertEquals(alicePublic, derivePublicKey("  $alicePrivate\n"))
    }

    @Test
    fun blankOrMalformedYieldsNull() {
        assertNull(derivePublicKey(""))
        assertNull(derivePublicKey("not a key"))
        assertNull(derivePublicKey(alicePrivate.drop(4)))
    }
}
