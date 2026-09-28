// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.muelli.kabelwacht.MainActivity
import com.github.muelli.kabelwacht.R
import com.github.muelli.kabelwacht.appContainer
import com.github.muelli.kabelwacht.data.TunnelProfile
import com.github.muelli.kabelwacht.data.WidgetBindingStore
import com.github.muelli.kabelwacht.ui.theme.KabelWachtTheme

/**
 * Asks which tunnel a widget should toggle. Launched by the launcher when the
 * widget is placed, again when the user reconfigures it (Android 12+), and by
 * the widget itself when the tunnel it was bound to has disappeared.
 *
 * "Automatic" stores no binding at all, which is the behaviour every widget
 * had before this screen existed: follow the active tunnel, else the most
 * recently used one.
 */
class TunnelWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Backing out must leave the widget unplaced, so cancel is the default
        // result until the user actually picks something.
        setResult(Activity.RESULT_CANCELED, resultIntent(appWidgetId))

        val bindings = WidgetBindingStore(this)
        val profiles = appContainer.repository.profiles.value
        val current = bindings.tunnelFor(appWidgetId)

        setContent {
            KabelWachtTheme {
                ConfigScreen(
                    profiles = profiles,
                    selected = current,
                    onPick = { name ->
                        bindings.bind(appWidgetId, name)
                        appContainer.renderWidgets()
                        setResult(Activity.RESULT_OK, resultIntent(appWidgetId))
                        finish()
                    },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    profiles: List<TunnelProfile>,
    selected: String?,
    onPick: (String?) -> Unit,
    onOpenApp: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(R.string.widget_config_no_tunnels),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenApp) { Text(stringResource(R.string.widget_config_open_app)) }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .wrapContentWidth()
                .widthIn(max = 840.dp),
        ) {
            ChoiceRow(
                title = stringResource(R.string.widget_config_automatic),
                subtitle = stringResource(R.string.widget_config_automatic_desc),
                selected = selected == null,
                onClick = { onPick(null) },
            )
            HorizontalDivider()
            profiles.forEach { profile ->
                ChoiceRow(
                    title = profile.name,
                    subtitle = null,
                    selected = selected == profile.name,
                    onClick = { onPick(profile.name) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
