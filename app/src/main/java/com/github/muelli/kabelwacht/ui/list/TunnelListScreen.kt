// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui.list

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.muelli.kabelwacht.R
import com.github.muelli.kabelwacht.appContainer
import com.github.muelli.kabelwacht.data.TunnelProfile
import com.github.muelli.kabelwacht.ui.AppViewModelProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelListScreen(
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onImport: () -> Unit,
    viewModel: TunnelListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val container = context.appContainer

    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeTunnel by viewModel.activeTunnel.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show the wg-quick text from an import in the edit screen for naming/review.
    fun receiveImport(raw: String) {
        container.pendingImport = raw
        onImport()
    }

    // --- QR scanning (zxing-android-embedded, no Google Play Services) ---
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { receiveImport(it) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) scanLauncher.launch(qrScanOptions(context.getString(R.string.scan_qr_prompt)))
        else viewModel.showMessage(context.getString(R.string.camera_permission_required))
    }
    fun startScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) scanLauncher.launch(qrScanOptions(context.getString(R.string.scan_qr_prompt)))
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // --- File import (.conf) ---
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) viewModel.showMessage(context.getString(R.string.file_read_failed))
            else receiveImport(text)
        }
    }

    // --- VPN consent, then activate ---
    var pendingToggleOn by remember { mutableStateOf<TunnelProfile?>(null) }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val profile = pendingToggleOn
        pendingToggleOn = null
        if (result.resultCode == Activity.RESULT_OK && profile != null) {
            viewModel.setActive(profile, up = true)
        } else {
            viewModel.showMessage(context.getString(R.string.vpn_permission_required))
        }
    }
    fun toggle(profile: TunnelProfile, up: Boolean) {
        if (!up) {
            viewModel.setActive(profile, up = false)
            return
        }
        val intent = viewModel.consentIntent()
        if (intent == null) {
            viewModel.setActive(profile, up = true)
        } else {
            pendingToggleOn = profile
            consentLauncher.launch(intent)
        }
    }

    message?.let { text ->
        androidx.compose.runtime.LaunchedEffect(text) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    var addMenuOpen by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<TunnelProfile?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_tunnel))
                }
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.scan_qr)) },
                        leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, null) },
                        onClick = { addMenuOpen = false; startScan() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_file)) },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null) },
                        onClick = { addMenuOpen = false; fileLauncher.launch(arrayOf("*/*")) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_blank)) },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { addMenuOpen = false; onCreate() },
                    )
                }
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            EmptyState(padding)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(profiles, key = { it.name }) { profile ->
                    TunnelRow(
                        profile = profile,
                        isActive = activeTunnel == profile.name,
                        onToggle = { up -> toggle(profile, up) },
                        onClick = { onEdit(profile.name) },
                        onEditClick = { onEdit(profile.name) },
                        onDeleteClick = { toDelete = profile },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    toDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_tunnel_title)) },
            text = { Text(stringResource(R.string.delete_tunnel_message, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(profile)
                    toDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TunnelRow(
    profile: TunnelProfile,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(profile.name) },
        supportingContent = {
            Text(
                profileSummary(profile),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isActive, onCheckedChange = onToggle)
                Box {
                    androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = { menuOpen = false; onEditClick() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = { menuOpen = false; onDeleteClick() },
                        )
                    }
                }
            }
        },
    )
}

/** One-line summary: the first peer's endpoint, or a hint if none is set. */
private fun profileSummary(profile: TunnelProfile): String {
    val endpoint = profile.config.peers.firstOrNull()?.endpoint?.orElse(null)
    return endpoint?.toString() ?: "No endpoint configured"
}

private fun qrScanOptions(prompt: String) = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt(prompt)
    setBeepEnabled(false)
    setOrientationLocked(false)
}
