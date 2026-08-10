// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import com.wireguard.config.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the list of stored profiles.
 *
 * Wraps [ConfigStore] and exposes the current profiles as an observable [StateFlow]
 * that the UI collects. Mutations write through to disk and then refresh the flow.
 */
class TunnelRepository(private val store: ConfigStore) {

    private val _profiles = MutableStateFlow<List<TunnelProfile>>(emptyList())
    val profiles: StateFlow<List<TunnelProfile>> = _profiles.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _profiles.value = store.list().sortedBy { it.name.lowercase() }
    }

    fun get(name: String): TunnelProfile? = _profiles.value.firstOrNull { it.name == name }

    fun exists(name: String): Boolean = store.exists(name)

    /**
     * Name of a stored profile whose config is semantically identical to [config]
     * (same canonical wg-quick serialization), or null if there is none.
     */
    fun findDuplicate(config: Config): String? {
        val canonical = config.toWgQuickString()
        return _profiles.value.firstOrNull { it.config.toWgQuickString() == canonical }?.name
    }

    /** First free auto-generated name, for prefilling the editor of a new profile. */
    fun suggestName(): String = suggestName(::exists)

    /** Create or overwrite a profile with the given name and config. */
    fun save(name: String, config: Config) {
        store.save(name, config)
        refresh()
    }

    /**
     * Apply an edit that may also rename the profile. Renames first (so the config
     * ends up under the new file), then writes the possibly-changed config.
     */
    fun update(oldName: String, newName: String, config: Config) {
        if (oldName != newName) store.rename(oldName, newName)
        store.save(newName, config)
        refresh()
    }

    fun delete(name: String) {
        store.delete(name)
        refresh()
    }

    companion object {
        /**
         * First "wg-tunnel-<n>" (n >= 1) for which [taken] is false. Tunnel names
         * are capped at 15 characters (they double as the interface name), which
         * this scheme respects up to n = 99999.
         */
        fun suggestName(taken: (String) -> Boolean): String =
            generateSequence(1) { it + 1 }.map { "wg-tunnel-$it" }.first { !taken(it) }
    }
}
