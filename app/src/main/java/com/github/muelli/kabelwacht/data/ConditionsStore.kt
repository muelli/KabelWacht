// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * File-backed persistence for per-tunnel run conditions.
 *
 * Each tunnel's rules live in a small `key = value` text file under
 * `filesDir/conditions/<name>.conf`, kept in a separate directory from the
 * WireGuard `.conf` files so automation rules never touch (or break) the
 * strictly-parsed wg-quick configs.
 */
class ConditionsStore(private val dir: File) {

    constructor(context: Context) : this(File(context.filesDir, CONDITIONS_DIR))

    init {
        dir.mkdirs()
    }

    private val _conditions = MutableStateFlow<Map<String, TunnelRunConditions>>(emptyMap())
    val conditions: StateFlow<Map<String, TunnelRunConditions>> = _conditions.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _conditions.value = listFromDisk()
    }

    private fun listFromDisk(): Map<String, TunnelRunConditions> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(CONF_SUFFIX) } ?: return emptyMap()
        val result = mutableMapOf<String, TunnelRunConditions>()
        for (file in files) {
            val name = file.name.removeSuffix(CONF_SUFFIX)
            val conditions = runCatching {
                TunnelRunConditions.parse(file.readText())
            }.getOrNull()
            if (conditions != null) {
                result[name] = conditions
            }
        }
        return result
    }

    fun get(name: String): TunnelRunConditions =
        _conditions.value[name] ?: TunnelRunConditions()

    fun save(name: String, conditions: TunnelRunConditions) {
        val file = fileFor(name)
        if (!conditions.enabled && conditions == TunnelRunConditions()) {
            // Optimization: if disabled and default, delete file if it exists
            if (file.exists()) file.delete()
        } else {
            file.writeText(conditions.toFormatString())
        }
        refresh()
    }

    fun delete(name: String) {
        fileFor(name).delete()
        refresh()
    }

    fun rename(oldName: String, newName: String) {
        if (oldName == newName) return
        val oldFile = fileFor(oldName)
        if (oldFile.exists()) {
            val newFile = fileFor(newName)
            oldFile.renameTo(newFile)
        }
        refresh()
    }

    private fun fileFor(name: String) = File(dir, "$name$CONF_SUFFIX")

    companion object {
        private const val CONDITIONS_DIR = "conditions"
        private const val CONF_SUFFIX = ".conf"
    }
}
