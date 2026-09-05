// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.vpn.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.muelli.kabelwacht.appContainer

/**
 * Resumes condition monitoring on device reboot if any tunnel has run conditions configured.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Read the store directly: it loads synchronously at construction, whereas
            // the engine's derived StateFlow starts on a background dispatcher and may
            // still hold its initial value this early in the process lifetime.
            val hasAutomations = context.appContainer.conditionsStore.conditions.value
                .any { it.value.enabled }
            if (hasAutomations) {
                ConditionMonitorService.sync(context, true)
            }
        }
    }
}
