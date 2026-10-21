// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TrafficFormatTest {
    @Test
    fun bytesBelowOneKibStayExact() {
        assertEquals("0 B", TrafficFormat.bytes(0, Locale.ROOT))
        assertEquals("1023 B", TrafficFormat.bytes(1023, Locale.ROOT))
    }

    @Test
    fun bytesScaleThroughBinaryUnits() {
        assertEquals("1.00 KiB", TrafficFormat.bytes(1024, Locale.ROOT))
        assertEquals("1.50 KiB", TrafficFormat.bytes(1536, Locale.ROOT))
        assertEquals("12.0 MiB", TrafficFormat.bytes(12L * 1024 * 1024, Locale.ROOT))
        assertEquals("512 GiB", TrafficFormat.bytes(512L * 1024 * 1024 * 1024, Locale.ROOT))
        assertEquals("2048 TiB", TrafficFormat.bytes(2048L * 1024 * 1024 * 1024 * 1024, Locale.ROOT))
    }

    @Test
    fun bytesUseTheLocaleDecimalSeparator() {
        assertEquals("1,50 KiB", TrafficFormat.bytes(1536, Locale.FRANCE))
    }

    @Test
    fun durationKeepsTheTwoMostSignificantUnits() {
        assertEquals("0 s", TrafficFormat.duration(0))
        assertEquals("59 s", TrafficFormat.duration(59))
        assertEquals("1 min", TrafficFormat.duration(60))
        assertEquals("59 min", TrafficFormat.duration(3599))
        assertEquals("2 h", TrafficFormat.duration(7200))
        assertEquals("2 h 5 min", TrafficFormat.duration(7500))
        assertEquals("3 d", TrafficFormat.duration(3 * 86400 + 100))
    }
}
