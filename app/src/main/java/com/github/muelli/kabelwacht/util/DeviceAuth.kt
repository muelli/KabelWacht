// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.util

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/**
 * Ask the user to re-confirm their identity with the device lock (biometrics or
 * PIN/pattern/password) before a sensitive action, e.g. exporting a private key.
 *
 * If the device has no secure lock screen there is nothing to authenticate
 * against, so the action proceeds; callers should still show their own warning.
 * [onResult] is invoked with true on success, false when the user cancels or
 * authentication errors out ("failed" attempts keep the prompt open).
 */
fun confirmDeviceCredential(
    context: Context,
    title: String,
    subtitle: String,
    onResult: (Boolean) -> Unit,
) {
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    if (keyguard?.isDeviceSecure != true) {
        onResult(true)
        return
    }

    val builder = BiometricPrompt.Builder(context)
        .setTitle(title)
        .setSubtitle(subtitle)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
    } else {
        @Suppress("DEPRECATION")
        builder.setDeviceCredentialAllowed(true)
    }

    builder.build().authenticate(
        CancellationSignal(),
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) =
                onResult(true)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) =
                onResult(false)
        },
    )
}
