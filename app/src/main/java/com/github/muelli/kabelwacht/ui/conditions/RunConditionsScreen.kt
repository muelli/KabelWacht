// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui.conditions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.muelli.kabelwacht.R
import com.github.muelli.kabelwacht.data.MobileConditionMode
import com.github.muelli.kabelwacht.data.NetworkStateSnapshot
import com.github.muelli.kabelwacht.data.WifiConditionMode
import com.github.muelli.kabelwacht.ui.AppViewModelProvider
import com.github.muelli.kabelwacht.ui.theme.MaxContentWidth
import com.github.muelli.kabelwacht.vpn.automation.ConditionMonitorService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RunConditionsScreen(
    tunnelName: String,
    onDone: () -> Unit,
    viewModel: RunConditionsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    LaunchedEffect(tunnelName) {
        viewModel.start(tunnelName)
    }

    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    // Android 12+ requires fine and coarse to be requested together (the user may
    // pick approximate-only, which cannot read SSIDs — we keep checking for fine).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasLocationPermission) {
            // Nudge the monitor service so it re-asserts its foreground type and
            // gains background SSID access right away (it stops itself again if
            // no tunnel has rules enabled).
            ConditionMonitorService.sync(context, true)
        }
    }

    val networkSnapshot by viewModel.networkSnapshot.collectAsStateWithLifecycle()
    val conditions = viewModel.conditions

    fun saveAndFinish() {
        viewModel.save()
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.run_conditions_title, tunnelName),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::saveAndFinish) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.save),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = ::saveAndFinish) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.save),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // Stay a readable, centered pane in wide (desktop/tablet) windows.
                .wrapContentWidth()
                .widthIn(max = MaxContentWidth)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Master enable switch
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.enable_run_conditions),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.run_conditions_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = conditions.enabled,
                        onCheckedChange = viewModel::onEnabledChange,
                    )
                }
            }

            // Live status card
            LiveStatusCard(
                snapshot = networkSnapshot,
                shouldRun = conditions.shouldRun(networkSnapshot),
                enabled = conditions.enabled,
            )

            // Wi-Fi Rules
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.section_wifi_rules),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = conditions.wifiEnabled,
                            onCheckedChange = viewModel::onWifiEnabledChange,
                        )
                    }

                    AnimatedVisibility(visible = conditions.wifiEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Mode selection
                            RadioOption(
                                label = stringResource(R.string.wifi_mode_any),
                                selected = conditions.wifiMode == WifiConditionMode.ANY,
                                onSelect = { viewModel.onWifiModeChange(WifiConditionMode.ANY) },
                            )
                            RadioOption(
                                label = stringResource(R.string.wifi_mode_exclude),
                                selected = conditions.wifiMode == WifiConditionMode.EXCLUDE_LIST,
                                onSelect = { viewModel.onWifiModeChange(WifiConditionMode.EXCLUDE_LIST) },
                            )
                            RadioOption(
                                label = stringResource(R.string.wifi_mode_include),
                                selected = conditions.wifiMode == WifiConditionMode.INCLUDE_LIST,
                                onSelect = { viewModel.onWifiModeChange(WifiConditionMode.INCLUDE_LIST) },
                            )

                            val needsSsidList = conditions.wifiMode != WifiConditionMode.ANY
                            AnimatedVisibility(visible = needsSsidList) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (!hasLocationPermission) {
                                        LocationPermissionNotice(onRequestPermission = {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                ),
                                            )
                                        })
                                    }

                                    // Add current Wi-Fi quick button
                                    val currentSsid = networkSnapshot.wifiSsid
                                    if (networkSnapshot.isWifi && currentSsid != null && !conditions.wifiSsids.contains(currentSsid)) {
                                        OutlinedButton(
                                            onClick = { viewModel.onAddSsid(currentSsid) },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Outlined.Wifi, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.add_current_wifi, currentSsid))
                                        }
                                    }

                                    // Manual SSID input
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = viewModel.ssidInput,
                                            onValueChange = { viewModel.ssidInput = it },
                                            label = { Text(stringResource(R.string.ssid_input_label)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(
                                            onClick = { viewModel.onAddSsid(viewModel.ssidInput) },
                                            enabled = viewModel.ssidInput.isNotBlank(),
                                        ) {
                                            Text(stringResource(R.string.add_ssid))
                                        }
                                    }

                                    // SSID chips
                                    if (conditions.wifiSsids.isEmpty()) {
                                        Text(
                                            stringResource(R.string.no_ssids_configured),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            conditions.wifiSsids.forEach { ssid ->
                                                FilterChip(
                                                    selected = true,
                                                    onClick = { viewModel.onRemoveSsid(ssid) },
                                                    label = { Text(ssid) },
                                                    trailingIcon = {
                                                        Icon(
                                                            Icons.Filled.Close,
                                                            contentDescription = stringResource(R.string.delete),
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Mobile Data Rules
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.section_mobile_rules),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    RadioOption(
                        label = stringResource(R.string.mobile_mode_always),
                        selected = conditions.mobileMode == MobileConditionMode.ALWAYS,
                        onSelect = { viewModel.onMobileModeChange(MobileConditionMode.ALWAYS) },
                    )
                    RadioOption(
                        label = stringResource(R.string.mobile_mode_never),
                        selected = conditions.mobileMode == MobileConditionMode.NEVER,
                        onSelect = { viewModel.onMobileModeChange(MobileConditionMode.NEVER) },
                    )
                    RadioOption(
                        label = stringResource(R.string.mobile_mode_only_roaming),
                        selected = conditions.mobileMode == MobileConditionMode.ONLY_ROAMING,
                        onSelect = { viewModel.onMobileModeChange(MobileConditionMode.ONLY_ROAMING) },
                    )
                    RadioOption(
                        label = stringResource(R.string.mobile_mode_never_roaming),
                        selected = conditions.mobileMode == MobileConditionMode.NEVER_ROAMING,
                        onSelect = { viewModel.onMobileModeChange(MobileConditionMode.NEVER_ROAMING) },
                    )
                }
            }

            // Advanced Rules (Ethernet & Metered)
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.section_advanced_conditions),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.ethernet_enabled),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = conditions.ethernetEnabled,
                            onCheckedChange = viewModel::onEthernetEnabledChange,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.unmetered_only),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(R.string.unmetered_only_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = conditions.requireUnmetered,
                            onCheckedChange = viewModel::onRequireUnmeteredChange,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LiveStatusCard(
    snapshot: NetworkStateSnapshot,
    shouldRun: Boolean,
    enabled: Boolean,
) {
    val connectionSummary = when {
        !snapshot.isConnected -> stringResource(R.string.status_network_disconnected)
        snapshot.isWifi -> {
            val ssid = snapshot.wifiSsid
            if (ssid != null) stringResource(R.string.status_network_wifi, ssid)
            else stringResource(R.string.status_network_wifi_unnamed)
        }
        snapshot.isMobile -> {
            val roamingTag = if (snapshot.isRoaming) stringResource(R.string.status_roaming_tag) else ""
            stringResource(R.string.status_network_mobile, roamingTag)
        }
        snapshot.isEthernet -> stringResource(R.string.status_network_ethernet)
        else -> stringResource(R.string.status_network_disconnected)
    } + if (snapshot.isMetered) stringResource(R.string.status_metered_tag) else ""

    val evalSummary = when {
        !enabled -> stringResource(R.string.status_eval_disabled)
        shouldRun -> stringResource(R.string.status_eval_running)
        else -> stringResource(R.string.status_eval_paused)
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (enabled && shouldRun) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.section_live_status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                connectionSummary,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                evalSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled && shouldRun) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LocationPermissionNotice(onRequestPermission: () -> Unit) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.location_permission_needed_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                stringResource(R.string.location_permission_needed_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            OutlinedButton(
                onClick = onRequestPermission,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
}
