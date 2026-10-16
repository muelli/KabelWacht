// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.util

import java.util.Locale

/** Compact, pure formatting of traffic counters and durations for the stats lines. */
object TrafficFormat {
    private val UNITS = arrayOf("KiB", "MiB", "GiB", "TiB")

    /** "512 B", "1.5 KiB", "12.3 MiB" — binary units, like the WireGuard tools. */
    fun bytes(
        bytes: Long,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < UNITS.lastIndex) {
            value /= 1024
            unit++
        }
        val pattern =
            if (value < 10) {
                "%.2f %s"
            } else if (value < 100) {
                "%.1f %s"
            } else {
                "%.0f %s"
            }
        return String.format(locale, pattern, value, UNITS[unit])
    }

    /** "42 s", "5 min", "2 h 5 min", "3 d" — the two most significant units at most. */
    fun duration(seconds: Long): String =
        when {
            seconds < 60 -> {
                "$seconds s"
            }

            seconds < 3600 -> {
                "${seconds / 60} min"
            }

            seconds < 86400 -> {
                val minutes = seconds % 3600 / 60
                if (minutes == 0L) "${seconds / 3600} h" else "${seconds / 3600} h $minutes min"
            }

            else -> {
                "${seconds / 86400} d"
            }
        }
}
