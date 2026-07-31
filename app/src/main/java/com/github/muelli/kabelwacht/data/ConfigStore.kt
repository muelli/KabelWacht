// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import android.content.Context
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

/**
 * File-backed persistence for WireGuard profiles.
 *
 * Each profile is a single wg-quick `.conf` file under `filesDir/tunnels/`, named
 * after the tunnel. This mirrors WireGuard's own on-device file store and keeps the
 * data trivially inspectable/exportable. All I/O here touches at most a handful of
 * tiny files.
 */
class ConfigStore(context: Context) {

    private val dir: File = File(context.filesDir, TUNNELS_DIR).apply { mkdirs() }

    /** Parse and load every stored profile. Files that fail to parse are skipped. */
    fun list(): List<TunnelProfile> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(CONF_SUFFIX) }
            ?.mapNotNull { file ->
                val name = file.name.removeSuffix(CONF_SUFFIX)
                runCatching { TunnelProfile(name, parse(file.readText())) }.getOrNull()
            }
            ?: emptyList()

    fun exists(name: String): Boolean = fileFor(name).exists()

    /**
     * Persist [config] under [name], creating or overwriting the file.
     * @throws IllegalArgumentException if [name] is not a valid tunnel name.
     */
    fun save(name: String, config: Config) {
        require(!Tunnel.isNameInvalid(name)) { "Invalid tunnel name: $name" }
        fileFor(name).writeText(config.toWgQuickString())
    }

    fun delete(name: String) {
        fileFor(name).delete()
    }

    /**
     * Rename a profile, preserving its config. No-op if [oldName] == [newName].
     * @throws IllegalArgumentException if [newName] is invalid or already taken.
     */
    fun rename(oldName: String, newName: String) {
        if (oldName == newName) return
        require(!Tunnel.isNameInvalid(newName)) { "Invalid tunnel name: $newName" }
        require(!exists(newName)) { "A tunnel named $newName already exists" }
        fileFor(oldName).renameTo(fileFor(newName))
    }

    private fun fileFor(name: String) = File(dir, "$name$CONF_SUFFIX")

    companion object {
        private const val TUNNELS_DIR = "tunnels"
        private const val CONF_SUFFIX = ".conf"

        /** Parse wg-quick text into a [Config]; throws [com.wireguard.config.BadConfigException] on invalid input. */
        fun parse(raw: String): Config =
            Config.parse(BufferedReader(StringReader(raw)))
    }
}
