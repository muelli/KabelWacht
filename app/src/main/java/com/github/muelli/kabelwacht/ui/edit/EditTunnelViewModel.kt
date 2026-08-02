// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.github.muelli.kabelwacht.AppContainer
import com.github.muelli.kabelwacht.data.ConfigStore
import com.github.muelli.kabelwacht.data.TunnelRepository
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair

/** Editable projection of one `[Peer]` section. */
class PeerFormState {
    var publicKey by mutableStateOf("")
    var presharedKey by mutableStateOf("")
    var endpoint by mutableStateOf("")
    var allowedIps by mutableStateOf("")
    var persistentKeepalive by mutableStateOf("")
}

/**
 * Backs the create/edit screen. A single screen handles three entry points —
 * create (blank), edit an existing profile, and import review (QR/file) — and
 * two views of the same data:
 *
 *  - **Form**: typed Interface/Peer sections (the default).
 *  - **Raw**: the full wg-quick text, collapsed in an expander.
 *
 * The wg-quick text is the canonical semantics: opening the raw view serializes
 * the form; closing it (or saving) parses the text back. Text that does not
 * parse keeps the raw view open with the error, so nothing is ever lost.
 */
class EditTunnelViewModel(
    private val repository: TunnelRepository,
    private val container: AppContainer,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var nameError by mutableStateOf<String?>(null)
        private set
    var configError by mutableStateOf<String?>(null)
        private set

    // Interface section.
    var privateKey by mutableStateOf("")
        private set
    var addresses by mutableStateOf("")
        private set
    var dns by mutableStateOf("")
        private set
    var listenPort by mutableStateOf("")
        private set
    var mtu by mutableStateOf("")
        private set

    val peers = mutableStateListOf<PeerFormState>()

    // Raw view.
    var rawExpanded by mutableStateOf(false)
        private set
    var rawText by mutableStateOf("")
        private set

    /**
     * Interface keys the form does not surface (Included/ExcludedApplications),
     * carried through form round-trips so they are not silently dropped.
     */
    private var extraInterfaceLines: List<String> = emptyList()

    /** Name of the profile being edited, or null when creating/importing. */
    private var originalName: String? = null
    private var initialized = false

    val isEditing: Boolean get() = originalName != null

    /** Call once when the screen is first composed. [editName] is null for create/import. */
    fun start(editName: String?) {
        if (initialized) return
        initialized = true

        val text = when {
            editName != null -> repository.get(editName)?.also { originalName = it.name }
                ?.let { name = it.name; it.config.toWgQuickString() }
            else -> container.pendingImport?.also { container.pendingImport = null }
        }

        if (text == null) {
            // Blank creation: one empty peer to fill in.
            peers.add(PeerFormState())
            return
        }
        try {
            fillFormFrom(ConfigStore.parse(text))
        } catch (e: Exception) {
            // Not parseable (yet): fall back to the raw editor so nothing is lost.
            rawText = text
            rawExpanded = true
            configError = e.message ?: "Invalid WireGuard configuration"
        }
    }

    fun onNameChange(value: String) { name = value; nameError = null }
    fun onPrivateKeyChange(value: String) { privateKey = value; configError = null }
    fun onAddressesChange(value: String) { addresses = value; configError = null }
    fun onDnsChange(value: String) { dns = value; configError = null }
    fun onListenPortChange(value: String) { listenPort = value; configError = null }
    fun onMtuChange(value: String) { mtu = value; configError = null }
    fun onRawTextChange(value: String) { rawText = value; configError = null }

    /** Fill the empty private-key field with a freshly generated key. */
    fun generatePrivateKey() {
        privateKey = KeyPair().privateKey.toBase64()
        configError = null
    }

    fun addPeer() = peers.add(PeerFormState())
    fun removePeer(index: Int) { peers.removeAt(index); configError = null }

    /**
     * Toggle the raw expander. Opening serializes the form into text; closing
     * parses the text back into the form (and stays open on a parse error).
     */
    fun toggleRaw() {
        if (!rawExpanded) {
            rawText = buildRawFromForm()
            rawExpanded = true
        } else {
            try {
                fillFormFrom(ConfigStore.parse(rawText))
                rawExpanded = false
                configError = null
            } catch (e: Exception) {
                configError = e.message ?: "Invalid WireGuard configuration"
            }
        }
    }

    /** Validate and persist. Returns true on success; otherwise sets the error fields. */
    fun save(): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            nameError = "Name is required"
            return false
        }
        if (Tunnel.isNameInvalid(trimmedName)) {
            nameError = "Use 1–15 chars: letters, digits, and _=+.-"
            return false
        }
        if (trimmedName != originalName && repository.exists(trimmedName)) {
            nameError = "A tunnel named \"$trimmedName\" already exists"
            return false
        }

        val text = if (rawExpanded) rawText else buildRawFromForm()
        val config = try {
            ConfigStore.parse(text)
        } catch (e: BadConfigException) {
            configError = e.message ?: "Invalid WireGuard configuration"
            return false
        } catch (e: Exception) {
            configError = e.message ?: "Could not read configuration"
            return false
        }

        val existing = originalName
        if (existing == null) {
            repository.save(trimmedName, config)
        } else {
            repository.update(existing, trimmedName, config)
        }
        return true
    }

    private fun fillFormFrom(config: Config) {
        val itf = config.getInterface()
        privateKey = itf.keyPair.privateKey.toBase64()
        addresses = itf.addresses.joinToString(", ")
        dns = (itf.dnsServers.mapNotNull { it.hostAddress } + itf.dnsSearchDomains)
            .joinToString(", ")
        listenPort = itf.listenPort.map { it.toString() }.orElse("")
        mtu = itf.mtu.map { it.toString() }.orElse("")
        extraInterfaceLines = buildList {
            if (itf.includedApplications.isNotEmpty()) {
                add("IncludedApplications = ${itf.includedApplications.joinToString(", ")}")
            }
            if (itf.excludedApplications.isNotEmpty()) {
                add("ExcludedApplications = ${itf.excludedApplications.joinToString(", ")}")
            }
        }
        peers.clear()
        config.peers.forEach { peer ->
            peers.add(
                PeerFormState().apply {
                    publicKey = peer.publicKey.toBase64()
                    presharedKey = peer.preSharedKey.map { it.toBase64() }.orElse("")
                    endpoint = peer.endpoint.map { it.toString() }.orElse("")
                    allowedIps = peer.allowedIps.joinToString(", ")
                    persistentKeepalive = peer.persistentKeepalive.map { it.toString() }.orElse("")
                },
            )
        }
    }

    private fun buildRawFromForm(): String = buildString {
        appendLine("[Interface]")
        appendKV("PrivateKey", privateKey)
        appendKV("Address", addresses)
        appendKV("DNS", dns)
        appendKV("ListenPort", listenPort)
        appendKV("MTU", mtu)
        extraInterfaceLines.forEach { appendLine(it) }
        peers.forEach { peer ->
            appendLine()
            appendLine("[Peer]")
            appendKV("PublicKey", peer.publicKey)
            appendKV("PresharedKey", peer.presharedKey)
            appendKV("Endpoint", peer.endpoint)
            appendKV("AllowedIPs", peer.allowedIps)
            appendKV("PersistentKeepalive", peer.persistentKeepalive)
        }
    }

    private fun StringBuilder.appendKV(key: String, value: String) {
        if (value.isNotBlank()) appendLine("$key = ${value.trim()}")
    }
}
