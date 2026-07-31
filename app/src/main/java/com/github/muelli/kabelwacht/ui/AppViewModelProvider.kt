package com.github.muelli.kabelwacht.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.muelli.kabelwacht.KabelWachtApp
import com.github.muelli.kabelwacht.ui.edit.EditTunnelViewModel
import com.github.muelli.kabelwacht.ui.list.TunnelListViewModel

/** [ViewModelProvider.Factory] instances wired from the [AppContainer]. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            val app = kabelWachtApp()
            TunnelListViewModel(app.container.repository, app.container.tunnelManager)
        }
        initializer {
            val app = kabelWachtApp()
            EditTunnelViewModel(app.container.repository, app.container)
        }
    }

    private fun CreationExtras.kabelWachtApp(): KabelWachtApp =
        (this[APPLICATION_KEY] as KabelWachtApp)
}
