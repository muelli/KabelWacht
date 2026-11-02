// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Small persisted settings. Mainly the always-on target: the name of the tunnel
 * to (re)connect when Android starts the VPN via the system "Always-on VPN"
 * feature. We track the most recently activated tunnel as that target.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val alwaysOnTunnel: Flow<String?> = store.data.map { it[ALWAYS_ON_TUNNEL] }

    suspend fun setAlwaysOnTunnel(name: String?) {
        store.edit { prefs ->
            if (name == null) prefs.remove(ALWAYS_ON_TUNNEL) else prefs[ALWAYS_ON_TUNNEL] = name
        }
    }

    /** Follow a tunnel rename, so the always-on target does not point at a name that no longer exists. */
    suspend fun renameAlwaysOnTunnel(oldName: String, newName: String) {
        store.edit { prefs ->
            if (prefs[ALWAYS_ON_TUNNEL] == oldName) prefs[ALWAYS_ON_TUNNEL] = newName
        }
    }

    /**
     * Records that the notification permission prompt was offered on connect.
     * Returns true only the first time, so connecting never nags again.
     */
    suspend fun claimNotificationPrompt(): Boolean {
        if (store.data.first()[NOTIFICATION_PROMPTED] == true) return false
        store.edit { it[NOTIFICATION_PROMPTED] = true }
        return true
    }

    private companion object {
        val ALWAYS_ON_TUNNEL = stringPreferencesKey("always_on_tunnel")
        val NOTIFICATION_PROMPTED = booleanPreferencesKey("notification_prompted")
    }
}
