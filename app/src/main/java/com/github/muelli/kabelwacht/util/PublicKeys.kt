// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyFormatException
import com.wireguard.crypto.KeyPair

/**
 * The interface public key belonging to [privateKey] (base64), or null when the
 * text is not a valid WireGuard key. This is what the peer's admin needs.
 */
fun derivePublicKey(privateKey: String): String? = try {
    KeyPair(Key.fromBase64(privateKey.trim())).publicKey.toBase64()
} catch (_: KeyFormatException) {
    null
}

/**
 * Put a public key on the clipboard. Unlike a private key it is not secret.
 *
 * Returns true when the caller should confirm the copy itself: from Android 13
 * the system already shows its own confirmation.
 */
fun copyPublicKey(context: Context, label: String, publicKey: String): Boolean {
    // Not marked EXTRA_IS_SENSITIVE (the default), so it may show in previews.
    val clip = ClipData.newPlainText(label, publicKey)
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}
