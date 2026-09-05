// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.muelli.kabelwacht.KabelWachtApp
import com.github.muelli.kabelwacht.ui.conditions.RunConditionsViewModel
import com.github.muelli.kabelwacht.ui.edit.EditTunnelViewModel
import com.github.muelli.kabelwacht.ui.list.TunnelListViewModel

/** [ViewModelProvider.Factory] instances wired from the [AppContainer]. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            val app = kabelWachtApp()
            TunnelListViewModel(
                repository = app.container.repository,
                tunnelManager = app.container.tunnelManager,
                conditionsStore = app.container.conditionsStore,
                automationEngine = app.container.automationEngine,
            )
        }
        initializer {
            val app = kabelWachtApp()
            EditTunnelViewModel(app.container.repository, app.container)
        }
        initializer {
            val app = kabelWachtApp()
            RunConditionsViewModel(
                conditionsStore = app.container.conditionsStore,
                networkMonitor = app.container.networkMonitor,
            )
        }
    }

    private fun CreationExtras.kabelWachtApp(): KabelWachtApp =
        (this[APPLICATION_KEY] as KabelWachtApp)
}
