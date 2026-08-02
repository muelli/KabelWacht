// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.muelli.kabelwacht.R
import com.github.muelli.kabelwacht.ui.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTunnelScreen(
    editName: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: EditTunnelViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    LaunchedEffect(editName) { viewModel.start(editName) }

    val title = if (viewModel.isEditing) {
        stringResource(R.string.edit_tunnel_title)
    } else {
        stringResource(R.string.new_tunnel_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { if (viewModel.save()) onDone() }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                isError = viewModel.nameError != null,
                supportingText = viewModel.nameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth(),
            )

            viewModel.configError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            // ----- [Interface] -----
            SectionCard(title = stringResource(R.string.section_interface)) {
                SecretField(
                    value = viewModel.privateKey,
                    onValueChange = viewModel::onPrivateKeyChange,
                    label = stringResource(R.string.field_private_key),
                    onGenerate = viewModel::generatePrivateKey,
                )
                PlainField(viewModel.addresses, viewModel::onAddressesChange, stringResource(R.string.field_addresses))
                PlainField(viewModel.dns, viewModel::onDnsChange, stringResource(R.string.field_dns))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        viewModel.listenPort, viewModel::onListenPortChange,
                        stringResource(R.string.field_listen_port), Modifier.weight(1f),
                    )
                    NumberField(
                        viewModel.mtu, viewModel::onMtuChange,
                        stringResource(R.string.field_mtu), Modifier.weight(1f),
                    )
                }
            }

            // ----- [Peer] sections -----
            viewModel.peers.forEachIndexed { index, peer ->
                SectionCard(
                    title = if (viewModel.peers.size > 1) {
                        stringResource(R.string.section_peer_n, index + 1)
                    } else {
                        stringResource(R.string.section_peer)
                    },
                    onRemove = { viewModel.removePeer(index) },
                ) {
                    PlainField(peer.publicKey, { peer.publicKey = it }, stringResource(R.string.field_public_key))
                    SecretField(
                        value = peer.presharedKey,
                        onValueChange = { peer.presharedKey = it },
                        label = stringResource(R.string.field_preshared_key),
                    )
                    PlainField(peer.endpoint, { peer.endpoint = it }, stringResource(R.string.field_endpoint))
                    PlainField(peer.allowedIps, { peer.allowedIps = it }, stringResource(R.string.field_allowed_ips))
                    NumberField(
                        peer.persistentKeepalive, { peer.persistentKeepalive = it },
                        stringResource(R.string.field_keepalive), Modifier.fillMaxWidth(),
                    )
                }
            }

            OutlinedButton(onClick = viewModel::addPeer) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.add_peer), modifier = Modifier.padding(start = 8.dp))
            }

            // ----- Raw configuration (collapsed by default) -----
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::toggleRaw)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.raw_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.raw_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (viewModel.rawExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
                AnimatedVisibility(visible = viewModel.rawExpanded) {
                    OutlinedTextField(
                        value = viewModel.rawText,
                        onValueChange = viewModel::onRawTextChange,
                        label = { Text(stringResource(R.string.field_config)) },
                        placeholder = { Text(stringResource(R.string.field_config_hint)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                        minLines = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    onRemove: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.remove_peer),
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun PlainField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** A masked credential field with a visibility toggle; offers key generation while blank. */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onGenerate: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
        ),
        trailingIcon = {
            if (value.isBlank() && onGenerate != null) {
                IconButton(onClick = onGenerate) {
                    Icon(
                        Icons.Outlined.Autorenew,
                        contentDescription = stringResource(R.string.generate_key),
                    )
                }
            } else {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.toggle_secret_visibility),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
