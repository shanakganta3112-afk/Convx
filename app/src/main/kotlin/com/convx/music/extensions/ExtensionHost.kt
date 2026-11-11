/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.extensions

import com.convx.extension.jiosaavn.JioSaavnExtension

/**
 * The one place the built-in extensions are instantiated.
 *
 * Both the Hilt graph (for search and browse) and [ExtensionStreams] (called from the
 * static `YTPlayerUtils` playback path, which has no injection) share these instances,
 * so the HTTP client behind each extension is created once per process.
 */
object ExtensionHost {
    val jioSaavn: JioSaavnExtension by lazy { JioSaavnExtension() }
}
