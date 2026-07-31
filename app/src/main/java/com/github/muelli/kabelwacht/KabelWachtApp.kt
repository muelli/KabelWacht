// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht

import android.app.Application
import android.content.Context
import com.github.muelli.kabelwacht.data.ConfigStore
import com.github.muelli.kabelwacht.data.TunnelRepository
import com.github.muelli.kabelwacht.vpn.TunnelManager

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
    val repository: TunnelRepository = TunnelRepository(ConfigStore(context))
    val tunnelManager: TunnelManager = TunnelManager(context)

    /**
     * Hands off raw wg-quick text from a QR scan or file import to the edit screen.
     * Read-once: the edit screen consumes it and clears it.
     */
    var pendingImport: String? = null
}

/** Convenience accessor for the container from anywhere with a [Context]. */
val Context.appContainer: AppContainer
    get() = (applicationContext as KabelWachtApp).container
