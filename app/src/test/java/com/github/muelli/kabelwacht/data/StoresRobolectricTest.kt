// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** File- and DataStore-backed stores, run against Robolectric's fake Context. */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one builds the container, which loads wireguard-go.
@Config(sdk = [34], application = android.app.Application::class)
class StoresRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val valid = """
        [Interface]
        PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
        Address = 10.0.0.2/32

        [Peer]
        PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
        Endpoint = 192.95.5.69:51820
        AllowedIPs = 0.0.0.0/0
    """.trimIndent()

    private fun tunnelsDir() = File(context.filesDir, "tunnels").apply { mkdirs() }

    @Test
    fun rename_moves_the_config_file() {
        val store = ConfigStore(context)
        store.save("old", ConfigStore.parse(valid))
        store.rename("old", "new")
        assertTrue(store.exists("new"))
        assertTrue(!store.exists("old"))
    }

    @Test
    fun unparseable_profile_is_reported_invalid_not_listed() {
        val store = ConfigStore(context)
        store.save("good", ConfigStore.parse(valid))
        File(tunnelsDir(), "broken.conf").writeText("[Interface]\nPrivateKey = nope\n")

        assertEquals(listOf("good"), store.list().map { it.name })
        assertEquals(listOf("broken"), store.listInvalid())
    }

    @Test
    fun repository_exposes_invalid_profiles_and_can_delete_them() {
        File(tunnelsDir(), "broken.conf").writeText("garbage")
        val repo = TunnelRepository(ConfigStore(context))
        assertEquals(listOf("broken"), repo.invalidProfiles.value)

        repo.delete("broken")
        assertTrue(repo.invalidProfiles.value.isEmpty())
    }

    @Test
    fun update_with_rename_notifies_the_rename_hook() {
        val renames = mutableListOf<Pair<String, String>>()
        val repo = TunnelRepository(ConfigStore(context)) { old, new -> renames += old to new }
        repo.save("old", ConfigStore.parse(valid))

        repo.update("old", "new", ConfigStore.parse(valid))
        repo.update("new", "new", ConfigStore.parse(valid))

        assertEquals(listOf("old" to "new"), renames)
        assertEquals(listOf("new"), repo.profiles.value.map { it.name })
    }

    @Test
    fun always_on_target_follows_a_rename() = runBlocking {
        val settings = SettingsStore(context)
        settings.setAlwaysOnTunnel("old")

        settings.renameAlwaysOnTunnel("other", "x")
        assertEquals("old", settings.alwaysOnTunnel.first())

        settings.renameAlwaysOnTunnel("old", "new")
        assertEquals("new", settings.alwaysOnTunnel.first())

        settings.setAlwaysOnTunnel(null)
        settings.renameAlwaysOnTunnel("new", "newer")
        assertNull(settings.alwaysOnTunnel.first())
    }
}
