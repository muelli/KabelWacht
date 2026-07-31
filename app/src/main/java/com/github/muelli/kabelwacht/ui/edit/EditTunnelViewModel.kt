package com.github.muelli.kabelwacht.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.github.muelli.kabelwacht.AppContainer
import com.github.muelli.kabelwacht.data.ConfigStore
import com.github.muelli.kabelwacht.data.TunnelRepository
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException

/**
 * Backs the create/edit screen. A single screen handles three entry points:
 *  - **create** (blank),
 *  - **edit** an existing profile, and
 *  - **import** review (config prefilled from a QR scan or file).
 *
 * The wg-quick text is validated on save via [ConfigStore.parse].
 */
class EditTunnelViewModel(
    private val repository: TunnelRepository,
    private val container: AppContainer,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var rawConfig by mutableStateOf("")
        private set
    var nameError by mutableStateOf<String?>(null)
        private set
    var configError by mutableStateOf<String?>(null)
        private set

    /** Name of the profile being edited, or null when creating/importing. */
    private var originalName: String? = null
    private var initialized = false

    val isEditing: Boolean get() = originalName != null

    /** Call once when the screen is first composed. [editName] is null for create/import. */
    fun start(editName: String?) {
        if (initialized) return
        initialized = true

        if (editName != null) {
            repository.get(editName)?.let { profile ->
                originalName = profile.name
                name = profile.name
                rawConfig = profile.config.toWgQuickString()
            }
        } else {
            // Consume a pending QR/file import, if any.
            container.pendingImport?.let { imported ->
                rawConfig = imported
                container.pendingImport = null
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
        nameError = null
    }

    fun onConfigChange(value: String) {
        rawConfig = value
        configError = null
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

        val config = try {
            ConfigStore.parse(rawConfig)
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
}
