/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.viewmodels

import androidx.lifecycle.ViewModel
import com.convx.extension.api.MusicExtension
import com.convx.music.extensions.ExtensionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Supplies the extension list to the settings page.
 *
 * Extensions are compiled in for now, so the list is static and needs no loading
 * state; when installable extensions land, this is where the installed set becomes a
 * flow.
 */
@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    registry: ExtensionRegistry,
) : ViewModel() {
    val installed: List<MusicExtension> = registry.installed
}
