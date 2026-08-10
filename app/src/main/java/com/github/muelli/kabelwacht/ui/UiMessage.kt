// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A user-facing message a ViewModel can emit without resolving Android
 * resources itself: either a string resource (with format arguments) or raw
 * pass-through text (details from lower layers, e.g. parser errors).
 * Resolution happens in the composable, so every static message stays
 * translatable and reacts to locale changes.
 */
class UiMessage private constructor(
    @param:StringRes private val resId: Int?,
    private val args: List<Any>,
    private val raw: String?,
) {
    constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList(), null)
    constructor(raw: String) : this(null, emptyList(), raw)

    @Composable
    fun resolve(): String = raw ?: stringResource(resId!!, *args.toTypedArray())
}
