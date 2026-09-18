// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.github.muelli.kabelwacht.MainActivity
import com.github.muelli.kabelwacht.R
import com.github.muelli.kabelwacht.TunnelActionActivity
import com.github.muelli.kabelwacht.appContainer

/** What the widget shows: the active tunnel, or the one a tap would connect. */
data class TunnelWidgetState(val activeName: String?, val targetName: String?)

/**
 * Home-screen widget showing one tunnel and its state; a tap toggles it via
 * [TunnelActionActivity]. The tunnel shown is the active one, else the
 * remembered (most recently used) one, else the first profile. Re-rendered by
 * the AppContainer whenever profiles, the active tunnel, or the remembered
 * tunnel change — [onUpdate] only handles the widget being (re)placed.
 */
class TunnelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        context.appContainer.renderWidgets()
    }

    companion object {
        fun render(context: Context, state: TunnelWidgetState) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TunnelWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_tunnel)
            val target = state.targetName
            if (target == null) {
                views.setTextViewText(R.id.widget_name, context.getString(R.string.widget_no_tunnel))
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_open_app))
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            } else {
                val active = state.activeName == target
                views.setTextViewText(R.id.widget_name, target)
                views.setTextViewText(
                    R.id.widget_status,
                    context.getString(if (active) R.string.widget_connected else R.string.widget_tap_to_connect),
                )
                val toggle = Intent(context, TunnelActionActivity::class.java)
                    .setAction(TunnelActionActivity.ACTION_TOGGLE)
                    .putExtra(TunnelActionActivity.EXTRA_TUNNEL, target)
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        // Distinct requestCode per tunnel so extras never collide.
                        target.hashCode(),
                        toggle,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
