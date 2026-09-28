// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import android.content.Context

/**
 * Which tunnel each home-screen widget is bound to, keyed by its
 * `appWidgetId`. A widget with no entry follows the automatic pick (the
 * active tunnel, else the most recently used one).
 *
 * Backed by SharedPreferences rather than DataStore because the widget
 * provider and its configuration activity need these values synchronously,
 * inside broadcast and activity callbacks that cannot wait on a Flow.
 */
class WidgetBindingStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("widget_bindings", Context.MODE_PRIVATE)

    /** Tunnel bound to [appWidgetId], or null when it follows the automatic pick. */
    fun tunnelFor(appWidgetId: Int): String? = prefs.getString(key(appWidgetId), null)

    /** Bind [appWidgetId] to [name], or clear the binding when [name] is null. */
    fun bind(appWidgetId: Int, name: String?) {
        prefs.edit().apply {
            if (name == null) remove(key(appWidgetId)) else putString(key(appWidgetId), name)
        }.apply()
    }

    /** Drop bindings for widgets the user removed, so ids cannot pile up. */
    fun forget(appWidgetIds: IntArray) {
        prefs.edit().apply { appWidgetIds.forEach { remove(key(it)) } }.apply()
    }

    /**
     * Follow a tunnel rename, since the name is the profile's identity. Without
     * this a rename would silently orphan every widget bound to that tunnel.
     */
    fun rename(oldName: String, newName: String) {
        if (oldName == newName) return
        val stale = prefs.all.filterValues { it == oldName }.keys
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach { putString(it, newName) } }.apply()
    }

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"
}
