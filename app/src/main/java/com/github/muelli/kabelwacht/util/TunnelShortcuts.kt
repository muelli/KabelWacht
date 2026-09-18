// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.muelli.kabelwacht.R
import com.github.muelli.kabelwacht.TunnelActionActivity
import com.github.muelli.kabelwacht.data.TunnelProfile

/**
 * Publishes one launcher shortcut per tunnel (long-press the app icon), each
 * toggling its tunnel via [TunnelActionActivity]. Re-synced whenever the
 * profile list changes so renames and deletions never leave a dead shortcut.
 */
object TunnelShortcuts {

    fun sync(context: Context, profiles: List<TunnelProfile>) {
        // Launchers show ~4 shortcuts; publishing more than the platform cap throws.
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(4)
        val shortcuts = profiles.take(max).map { profile ->
            val intent = Intent(context, TunnelActionActivity::class.java)
                .setAction(TunnelActionActivity.ACTION_TOGGLE)
                .putExtra(TunnelActionActivity.EXTRA_TUNNEL, profile.name)
            ShortcutInfoCompat.Builder(context, "tunnel:${profile.name}")
                .setShortLabel(profile.name)
                .setLongLabel(context.getString(R.string.shortcut_toggle_label, profile.name))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_quick_tunnel))
                .setIntent(intent)
                .build()
        }
        // Launchers may reject shortcuts (rate limits, profiles) — never crash for it.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
