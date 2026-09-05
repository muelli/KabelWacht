// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConditionsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun save_and_get_conditions() {
        val store = ConditionsStore(tempFolder.newFolder("conditions"))
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiEnabled = true,
            wifiMode = WifiConditionMode.EXCLUDE_LIST,
            wifiSsids = setOf("Home-Wifi"),
            mobileMode = MobileConditionMode.ONLY_ROAMING,
        )

        store.save("my-tunnel", conditions)
        val loaded = store.get("my-tunnel")

        assertEquals(conditions, loaded)
        assertTrue(store.conditions.value.containsKey("my-tunnel"))
    }

    @Test
    fun get_non_existent_tunnel_returns_default() {
        val store = ConditionsStore(tempFolder.newFolder("conditions"))
        val loaded = store.get("non-existent")
        assertEquals(TunnelRunConditions(), loaded)
        assertFalse(loaded.enabled)
    }

    @Test
    fun delete_removes_file_and_updates_flow() {
        val store = ConditionsStore(tempFolder.newFolder("conditions"))
        val conditions = TunnelRunConditions(enabled = true)
        store.save("tunnel-to-delete", conditions)
        assertTrue(store.get("tunnel-to-delete").enabled)

        store.delete("tunnel-to-delete")
        assertFalse(store.get("tunnel-to-delete").enabled)
        assertFalse(store.conditions.value.containsKey("tunnel-to-delete"))
    }

    @Test
    fun rename_preserves_conditions_under_new_name() {
        val store = ConditionsStore(tempFolder.newFolder("conditions"))
        val conditions = TunnelRunConditions(
            enabled = true,
            wifiMode = WifiConditionMode.INCLUDE_LIST,
            wifiSsids = setOf("Cafe"),
        )
        store.save("old-name", conditions)

        store.rename("old-name", "new-name")

        assertEquals(TunnelRunConditions(), store.get("old-name"))
        assertEquals(conditions, store.get("new-name"))
    }
}
