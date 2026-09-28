// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.github.muelli.kabelwacht.MainActivity
import com.github.muelli.kabelwacht.R
import com.github.muelli.kabelwacht.TunnelActionActivity
import com.github.muelli.kabelwacht.appContainer
import com.github.muelli.kabelwacht.data.WidgetBindingStore

/**
 * What the widgets need to know: which tunnel is up, which one the automatic
 * pick would act on, and which names still exist (so a widget bound to a
 * deleted tunnel can say so instead of silently toggling something else).
 */
data class TunnelWidgetState(
    val activeName: String?,
    val targetName: String?,
    val knownNames: Set<String> = emptySet(),
)

/**
 * Home-screen widget showing one tunnel and its state; a tap toggles it via
 * [TunnelActionActivity]. Each widget instance may be bound to a specific
 * tunnel through [TunnelWidgetConfigActivity]; an unbound one follows the
 * automatic pick, which is what every widget did before configuration
 * existed.
 *
 * Re-rendered by the AppContainer whenever profiles, the active tunnel or the
 * remembered tunnel change; [onUpdate] and [onAppWidgetOptionsChanged] cover
 * a widget being placed or resized.
 */
class TunnelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        context.appContainer.renderWidgets()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        // Resizing changes which layout variant fits.
        context.appContainer.renderWidgets()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetBindingStore(context).forget(appWidgetIds)
    }

    companion object {
        fun render(context: Context, state: TunnelWidgetState) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TunnelWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val bindings = WidgetBindingStore(context)
            ids.forEach { id -> renderOne(context, manager, bindings, id, state) }
        }

        private fun renderOne(
            context: Context,
            manager: AppWidgetManager,
            bindings: WidgetBindingStore,
            appWidgetId: Int,
            state: TunnelWidgetState,
        ) {
            val bound = bindings.tunnelFor(appWidgetId)
            // A bound tunnel that no longer exists must not silently fall back to
            // another one: the widget says so and a tap offers to re-pick.
            val boundMissing = bound != null && bound !in state.knownNames
            val target = if (bound != null) bound.takeUnless { boundMissing } else state.targetName
            val active = target != null && state.activeName == target

            val views = RemoteViews(context.packageName, layoutFor(manager, appWidgetId))
            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (active) R.drawable.widget_background_active else R.drawable.widget_background,
            )

            when {
                boundMissing -> {
                    views.setTextViewText(R.id.widget_name, bound)
                    views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_tunnel_missing))
                    views.setOnClickPendingIntent(R.id.widget_root, configureIntent(context, appWidgetId))
                }
                target == null -> {
                    views.setTextViewText(R.id.widget_name, context.getString(R.string.widget_no_tunnel))
                    views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_open_app))
                    views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                }
                else -> {
                    views.setTextViewText(R.id.widget_name, target)
                    views.setTextViewText(
                        R.id.widget_status,
                        context.getString(
                            if (active) R.string.widget_connected else R.string.widget_tap_to_connect,
                        ),
                    )
                    views.setContentDescription(
                        R.id.widget_root,
                        context.getString(R.string.shortcut_toggle_label, target),
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, toggleIntent(context, appWidgetId, target))
                }
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Pick a layout for the size the user resized this widget to. The
         * options bundle reports sizes in dp and is empty until the launcher
         * fills it in, so an unknown size falls back to the standard row.
         */
        private fun layoutFor(manager: AppWidgetManager, appWidgetId: Int): Int {
            val options = manager.getAppWidgetOptions(appWidgetId)
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            return when {
                minWidth in 1 until COMPACT_MAX_WIDTH_DP -> R.layout.widget_tunnel_compact
                minHeight >= LARGE_MIN_HEIGHT_DP -> R.layout.widget_tunnel_large
                else -> R.layout.widget_tunnel
            }
        }

        private fun toggleIntent(context: Context, appWidgetId: Int, tunnel: String): PendingIntent {
            val intent = Intent(context, TunnelActionActivity::class.java)
                .setAction(TunnelActionActivity.ACTION_TOGGLE)
                .putExtra(TunnelActionActivity.EXTRA_TUNNEL, tunnel)
            // Request code per widget id: distinct extras must not be collapsed
            // into one PendingIntent.
            return PendingIntent.getActivity(context, appWidgetId, intent, FLAGS)
        }

        private fun configureIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, TunnelWidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(context, appWidgetId, intent, FLAGS)
        }

        private fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), FLAGS)

        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        private const val COMPACT_MAX_WIDTH_DP = 110
        private const val LARGE_MIN_HEIGHT_DP = 110
    }
}
