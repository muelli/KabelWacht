// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht

import android.app.Application
import android.content.Context
import com.github.muelli.kabelwacht.data.ConfigStore
import com.github.muelli.kabelwacht.data.SettingsStore
import com.github.muelli.kabelwacht.data.TunnelRepository
import com.github.muelli.kabelwacht.vpn.TunnelManager
import com.wireguard.android.backend.GoBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application entry point. Owns the [AppContainer] (manual dependency injection —
 * the app is small enough not to need Hilt).
 */
class KabelWachtApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Long-lived singletons shared across the app. */
class AppContainer(context: Context) {
    val settings: SettingsStore = SettingsStore(context)
    val repository: TunnelRepository = TunnelRepository(ConfigStore(context))
    val tunnelManager: TunnelManager = TunnelManager(context, settings)

    /**
     * Hands off raw wg-quick text from a QR scan or file import to the edit screen.
     * Read-once: the edit screen consumes it and clears it.
     */
    var pendingImport: String? = null

    // Application-lifetime scope for work not tied to any screen (the always-on
    // callback may fire while no Activity exists).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // When Android starts the VPN via the system "Always-on VPN" feature, bring
        // up the remembered tunnel (falling back to the only/first one).
        GoBackend.setAlwaysOnCallback {
            scope.launch { activateAlwaysOn() }
        }
    }

    private suspend fun activateAlwaysOn() {
        repository.refresh()
        val name = settings.alwaysOnTunnel.first()
            ?: repository.profiles.value.singleOrNull()?.name
            ?: return
        val profile = repository.get(name) ?: return
        runCatching { tunnelManager.setTunnelState(profile, up = true) }
    }
}

/** Convenience accessor for the container from anywhere with a [Context]. */
val Context.appContainer: AppContainer
    get() = (applicationContext as KabelWachtApp).container
