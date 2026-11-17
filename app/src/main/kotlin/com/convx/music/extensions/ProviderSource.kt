/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.extensions

import androidx.compose.runtime.Composable
import com.convx.music.constants.SaavnOnlyKey
import com.convx.music.utils.rememberPreference

/**
 * The catalogue the app is currently serving, as a name to put in the interface.
 *
 * One source of truth for the label so search, home and settings cannot disagree with
 * what [com.convx.music.viewmodels.OnlineSearchViewModel] is actually querying.
 */
@Composable
fun rememberProviderLabel(): String {
    val (saavnOnly) = rememberPreference(SaavnOnlyKey, false)
    return if (saavnOnly) "JioSaavn" else "YouTube Music"
}
