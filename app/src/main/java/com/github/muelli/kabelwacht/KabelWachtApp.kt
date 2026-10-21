// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.github.muelli.kabelwacht.data.ConditionsStore
import com.github.muelli.kabelwacht.data.ConfigStore
import com.github.muelli.kabelwacht.data.SettingsStore
import com.github.muelli.kabelwacht.data.TunnelRepository
import com.github.muelli.kabelwacht.data.WidgetBindingStore
import com.github.muelli.kabelwacht.notification.TunnelNotifier
import com.github.muelli.kabelwacht.vpn.TunnelManager
import com.github.muelli.kabelwacht.vpn.retryOnConnectivity
import com.github.muelli.kabelwacht.vpn.automation.AutomationEngine
import com.github.muelli.kabelwacht.vpn.automation.ConditionMonitorService
import com.github.muelli.kabelwacht.vpn.automation.NetworkStateMonitor
import com.github.muelli.kabelwacht.util.TunnelShortcuts
import com.github.muelli.kabelwacht.widget.TunnelWidgetProvider
import com.github.muelli.kabelwacht.widget.TunnelWidgetState
import com.wireguard.android.backend.GoBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
    private val appContext = context.applicationContext
    val settings: SettingsStore = SettingsStore(context)
    val conditionsStore: ConditionsStore = ConditionsStore(context)
    val widgetBindings: WidgetBindingStore = WidgetBindingStore(context)
    val repository: TunnelRepository = TunnelRepository(ConfigStore(context), conditionsStore, widgetBindings)
    val tunnelManager: TunnelManager = TunnelManager(context, settings)
    val networkMonitor: NetworkStateMonitor = NetworkStateMonitor(context)
    private val notifier: TunnelNotifier = TunnelNotifier(context, tunnelManager)
    val automationEngine: AutomationEngine = AutomationEngine(
        conditionsStore = conditionsStore,
        repository = repository,
        tunnelManager = tunnelManager,
        networkMonitor = networkMonitor,
        onAutoConnectFailed = notifier::autoConnectFailed,
    )

    /**
     * Hands off raw wg-quick text from a QR scan or file import to the edit screen.
     * Read-once: the edit screen consumes it and clears it.
     */
    var pendingImport: String? = null

    // Application-lifetime scope for work not tied to any screen (the always-on
    // callback may fire while no Activity exists).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Declared before init: the collector launched there assigns it, and a property
    // initializer running afterwards would silently reset a fast first emission.
    @Volatile
    private var latestWidgetState: TunnelWidgetState? = null

    init {
        // Status-bar icon and live traffic while a tunnel is up; failure alerts.
        notifier.observe(scope)

        // Sync foreground monitor service lifecycle with active automations
        scope.launch {
            automationEngine.hasActiveAutomations.collect { active ->
                ConditionMonitorService.sync(context, active)
            }
        }

        // When Android starts the VPN via the system "Always-on VPN" feature, bring
        // up the remembered tunnel (falling back to the only/first one).
        GoBackend.setAlwaysOnCallback {
            scope.launch { activateAlwaysOn() }
        }

        // Keep the launcher shortcuts and the home-screen widget in step with the
        // profiles and the tunnel state.
        scope.launch {
            repository.profiles.collect { TunnelShortcuts.sync(appContext, it) }
        }
        scope.launch {
            combine(
                repository.profiles,
                tunnelManager.activeTunnel,
                settings.alwaysOnTunnel,
            ) { profiles, active, remembered ->
                TunnelWidgetState(
                    activeName = active,
                    targetName = active
                        ?: remembered?.takeIf { r -> profiles.any { it.name == r } }
                        ?: profiles.firstOrNull()?.name,
                    knownNames = profiles.mapTo(mutableSetOf()) { it.name },
                )
            }.collect { state ->
                latestWidgetState = state
                TunnelWidgetProvider.render(appContext, state)
            }
        }
    }

    /**
     * The tunnel the one-tap surfaces (widget, QS tile) act on and its state:
     * the latest observed combination, or a synchronous approximation before
     * the collector's first emission.
     */
    fun quickToggleState(): TunnelWidgetState =
        latestWidgetState ?: TunnelWidgetState(
            activeName = tunnelManager.activeTunnel.value,
            targetName = tunnelManager.activeTunnel.value
                ?: repository.profiles.value.firstOrNull()?.name,
            knownNames = repository.profiles.value.mapTo(mutableSetOf()) { it.name },
        )

    /** Re-render the widget(s) from the latest known state (e.g. one was just added). */
    fun renderWidgets() {
        TunnelWidgetProvider.render(appContext, quickToggleState())
    }

    /**
     * Toggle [name] from a one-tap entry point (shortcut/widget). Runs on the
     * application scope so a finishing trampoline activity cannot cancel it;
     * failures surface as a toast since no screen is around to show them.
     */
    fun toggleTunnel(name: String, up: Boolean) {
        val profile = repository.get(name) ?: return
        automationEngine.onUserManualToggle(name, up)
        scope.launch {
            val result = runCatching { tunnelManager.setTunnelState(profile, up) }
            result.exceptionOrNull()?.let { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        e.message ?: appContext.getString(R.string.error_tunnel_state),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private suspend fun activateAlwaysOn() {
        repository.refresh()
        val name = settings.alwaysOnTunnel.first()
            ?: repository.profiles.value.singleOrNull()?.name
            ?: return
        val profile = repository.get(name) ?: return
        // Right after boot there may be no network yet, so the endpoint's
        // hostname cannot resolve and the backend refuses (#34). Try again
        // once a network shows up, instead of leaving always-on silently off.
        val result = retryOnConnectivity(
            connected = networkMonitor.networkSnapshot.map { it.isConnected },
            maxAttempts = ALWAYS_ON_ATTEMPTS,
            connectTimeout = ALWAYS_ON_CONNECT_TIMEOUT,
            settleDelay = ALWAYS_ON_SETTLE_DELAY,
            onFailure = { attempt, e -> Log.w(TAG, "Always-on: bringing up $name failed (attempt $attempt)", e) },
        ) {
            // Someone else (the user, automation) got a tunnel up meanwhile.
            if (tunnelManager.activeTunnel.value == null) {
                tunnelManager.setTunnelState(profile, up = true)
            }
        }
        if (result.isFailure) notifier.alwaysOnFailed(name)
    }

    private companion object {
        const val TAG = "KabelWacht"
        const val ALWAYS_ON_ATTEMPTS = 5
        val ALWAYS_ON_CONNECT_TIMEOUT = 15.minutes
        val ALWAYS_ON_SETTLE_DELAY = 2.seconds
    }
}

/** Convenience accessor for the container from anywhere with a [Context]. */
val Context.appContainer: AppContainer
    get() = (applicationContext as KabelWachtApp).container
