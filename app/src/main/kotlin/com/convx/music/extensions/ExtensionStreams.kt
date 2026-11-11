/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.extensions

import android.content.Context
import com.convx.extension.api.ExtensionStream
import com.convx.music.constants.ExtensionEnabledKey
import com.convx.music.utils.dataStore
import com.convx.music.utils.get
import com.convx.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stream resolution for extension-owned ids, callable from the static playback path.
 *
 * `YTPlayerUtils` is an `object` with no injected graph, so it cannot take
 * [ExtensionRegistry]; this resolves against the shared [ExtensionHost] client and
 * reads the same enabled flag. Only the streaming capability is needed here.
 */
object ExtensionStreams {

    suspend fun resolve(context: Context, mediaId: String): ExtensionStream? {
        if (!ExtensionIds.isExtensionId(mediaId)) return null
        if (!context.dataStore.get(ExtensionEnabledKey, false)) return null
        val raw = ExtensionIds.rawId(mediaId) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { ExtensionHost.jioSaavn.stream(raw) }
                .onFailure { reportException(it) }
                .getOrNull()
        }
    }
}
